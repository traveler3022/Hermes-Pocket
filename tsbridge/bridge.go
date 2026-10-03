// Package tsbridge runs a Tailscale node inside the Hermes app with tsnet, so the
// app reaches the user's own server over their tailnet without the Tailscale app
// or Android's VPN slot. Kotlin calls it through gomobile.
//
// The app's HTTP client reaches the tailnet through a local HTTP CONNECT proxy
// (ProxyAddress/ProxySecret) that dials with the node and only to tailnet hosts.
package tsbridge

import (
	"bufio"
	"context"
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"os"
	"path/filepath"
	"runtime/debug"
	"slices"
	"strings"
	"sync"
	"time"

	"tailscale.com/client/local"
	"tailscale.com/envknob"
	"tailscale.com/logtail"
	"tailscale.com/net/netmon"
	"tailscale.com/net/tsaddr"
	"tailscale.com/tailcfg"
	"tailscale.com/tsnet"
	"tailscale.com/version"
)

// Platform is implemented in Kotlin.
type Platform interface {
	// InterfacesJSON lists the phone's network interfaces. Android denies
	// apps the netlink socket behind Go's net.Interfaces, so the list comes
	// from Java's NetworkInterface, as in the Tailscale Android app.
	InterfacesJSON() string
}

var (
	// lifeMu serializes Start and Stop. mu is never held across tsnet calls:
	// tsnet calls back into interfaces() while starting.
	lifeMu sync.Mutex

	mu          sync.Mutex
	srv         *tsnet.Server
	lc          *local.Client
	platform    Platform
	starting    bool
	proxyLn     net.Listener
	proxySecret string

	setupOnce sync.Once
)

// Start brings the node up, keeps its state in dir and opens the local proxy.
// It does nothing if the node is already running.
func Start(dir, hostname string, p Platform) (err error) {
	defer recovered(&err)
	lifeMu.Lock()
	defer lifeMu.Unlock()
	mu.Lock()
	running := srv != nil
	platform = p
	starting = !running
	mu.Unlock()
	if running {
		return nil
	}
	defer func() {
		mu.Lock()
		starting = false
		mu.Unlock()
	}()
	setupOnce.Do(func() {
		// Nothing is uploaded to log.tailscale.com.
		logtail.Disable()
		envknob.SetNoLogsNoSupport()
		netmon.RegisterInterfaceGetter(interfaces)
	})
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	// An app process has no HOME, cache or writable temp folder, so Tailscale's
	// logpolicy.LogsDir would find no place for its log state and panic ("no
	// safe place found to store log state"). TS_LOGS_DIR is read first and is
	// Tailscale's own, so it changes nothing for the rest of the app.
	logs := filepath.Join(dir, "logs")
	if err := os.MkdirAll(logs, 0o700); err != nil {
		return err
	}
	os.Setenv("TS_LOGS_DIR", logs)

	s := &tsnet.Server{
		Dir:      dir,
		Hostname: hostname,
		UserLogf: logf,
		Logf:     logf,
	}
	if err := s.Start(); err != nil {
		s.Close()
		return err
	}
	c, err := s.LocalClient()
	if err != nil {
		s.Close()
		return err
	}
	ln, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		s.Close()
		return err
	}
	secret := make([]byte, 24)
	if _, err := rand.Read(secret); err != nil {
		ln.Close()
		s.Close()
		return err
	}
	mu.Lock()
	srv, lc = s, c
	proxyLn, proxySecret = ln, hex.EncodeToString(secret)
	mu.Unlock()
	go serveProxy(ln, s, c, proxySecret)
	logf("tsbridge: started, tailscale %s", version.Short())
	return nil
}

// Stop shuts the node and the proxy down. The login stays in dir.
func Stop() {
	lifeMu.Lock()
	defer lifeMu.Unlock()
	mu.Lock()
	s, ln := srv, proxyLn
	srv, lc, proxyLn, proxySecret = nil, nil, nil, ""
	mu.Unlock()
	if ln != nil {
		ln.Close()
	}
	if s != nil {
		s.Close()
	}
}

func current() (*tsnet.Server, *local.Client) {
	mu.Lock()
	defer mu.Unlock()
	return srv, lc
}

func currentPlatform() Platform {
	mu.Lock()
	defer mu.Unlock()
	return platform
}

// ProxyAddress is the local proxy, "127.0.0.1:port", or "" while stopped.
func ProxyAddress() string {
	mu.Lock()
	defer mu.Unlock()
	if proxyLn == nil {
		return ""
	}
	return proxyLn.Addr().String()
}

// ProxySecret is the password of the local proxy: Proxy-Authorization is
// Basic "hermes:<secret>". Other apps on the phone can reach 127.0.0.1 too.
func ProxySecret() string {
	mu.Lock()
	defer mu.Unlock()
	return proxySecret
}

type peerJSON struct {
	Name    string `json:"name"`
	DNS     string `json:"dns"`
	IP      string `json:"ip"`
	OS      string `json:"os"`
	Online  bool   `json:"online"`
	CurAddr string `json:"curAddr"`
	Relay   string `json:"relay"`
}

type statusJSON struct {
	State   string     `json:"state"`
	AuthURL string     `json:"authURL"`
	Self    string     `json:"self"`
	SelfIP  string     `json:"selfIP"`
	User    string     `json:"user"`
	Tailnet string     `json:"tailnet"`
	Health  []string   `json:"health"`
	Peers   []peerJSON `json:"peers"`
	Error   string     `json:"error,omitempty"`
}

// Status reports the node's state, login URL and the tailnet's devices.
func Status() (ret string) {
	defer recoveredJSON(&ret)
	mu.Lock()
	c, isStarting := lc, starting
	mu.Unlock()
	if c == nil {
		if isStarting {
			return toJSON(statusJSON{State: "Starting"})
		}
		return toJSON(statusJSON{State: "Off"})
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	st, err := c.Status(ctx)
	if err != nil {
		return toJSON(statusJSON{State: "Error", Error: err.Error()})
	}
	out := statusJSON{
		State:   st.BackendState,
		AuthURL: st.AuthURL,
		Health:  st.Health,
	}
	if len(st.TailscaleIPs) > 0 {
		out.SelfIP = st.TailscaleIPs[0].String()
	}
	if st.Self != nil {
		out.Self = strings.TrimSuffix(st.Self.DNSName, ".")
		if u, ok := st.User[st.Self.UserID]; ok {
			out.User = u.LoginName
		}
	}
	if st.CurrentTailnet != nil {
		out.Tailnet = st.CurrentTailnet.Name
	}
	for _, p := range st.Peer {
		pj := peerJSON{
			Name:    p.HostName,
			DNS:     strings.TrimSuffix(p.DNSName, "."),
			OS:      p.OS,
			Online:  p.Online,
			CurAddr: p.CurAddr,
			Relay:   p.Relay,
		}
		if len(p.TailscaleIPs) > 0 {
			pj.IP = p.TailscaleIPs[0].String()
		}
		out.Peers = append(out.Peers, pj)
	}
	slices.SortFunc(out.Peers, func(a, b peerJSON) int {
		if a.Online != b.Online {
			if a.Online {
				return -1
			}
			return 1
		}
		return strings.Compare(a.Name, b.Name)
	})
	return toJSON(out)
}

// Login asks the control server for a fresh login URL; Status reports it.
func Login() (err error) {
	defer recovered(&err)
	_, c := current()
	if c == nil {
		return errors.New("not started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	return c.StartLoginInteractive(ctx)
}

// Logout removes this device from the tailnet.
func Logout() (err error) {
	defer recovered(&err)
	_, c := current()
	if c == nil {
		return errors.New("not started")
	}
	ctx, cancel := context.WithTimeout(context.Background(), 15*time.Second)
	defer cancel()
	return c.Logout(ctx)
}

type pingJSON struct {
	OK    bool    `json:"ok"`
	Ms    float64 `json:"ms"`
	Error string  `json:"error,omitempty"`
}

// Ping sends a TSMP ping, which goes through the WireGuard tunnel to the
// peer's Tailscale and back.
func Ping(ip string) (ret string) {
	defer recoveredJSON(&ret)
	_, c := current()
	if c == nil {
		return toJSON(pingJSON{Error: "not started"})
	}
	addr, err := netip.ParseAddr(ip)
	if err != nil {
		return toJSON(pingJSON{Error: err.Error()})
	}
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	res, err := c.Ping(ctx, addr, tailcfg.PingTSMP)
	if err != nil {
		return toJSON(pingJSON{Error: err.Error()})
	}
	if res.Err != "" {
		return toJSON(pingJSON{Error: res.Err})
	}
	return toJSON(pingJSON{OK: true, Ms: res.LatencySeconds * 1000})
}

// NetworkChanged is called on Android network changes. Without it the
// network monitor only re-checks every 10 minutes on Android.
func NetworkChanged(ifname, gateway string) {
	var err error
	defer recovered(&err)
	netmon.UpdateLastKnownDefaultRouteInterface(ifname)
	if gateway != "" {
		netmon.UpdateLastKnownDefaultGateway(gateway)
	}
	s, _ := current()
	if s == nil {
		return
	}
	if nm, ok := s.Sys().NetMon.GetOK(); ok {
		nm.InjectEvent()
	}
}

// Version is the Tailscale version linked in.
func Version() string {
	return version.Short()
}

func serveProxy(ln net.Listener, s *tsnet.Server, c *local.Client, secret string) {
	want := "Basic " + base64.StdEncoding.EncodeToString([]byte("hermes:"+secret))
	for {
		conn, err := ln.Accept()
		if err != nil {
			return
		}
		go proxyConn(conn, s, c, want)
	}
}

// proxyConn serves one CONNECT tunnel: authenticated, and only to a tailnet
// host, so the proxy is no way out to the internet for anything else.
func proxyConn(conn net.Conn, s *tsnet.Server, c *local.Client, want string) {
	defer conn.Close()
	defer func() {
		if r := recover(); r != nil {
			logf("tsbridge: proxy panic: %v\n%s", r, debug.Stack())
		}
	}()
	conn.SetReadDeadline(time.Now().Add(15 * time.Second))
	br := bufio.NewReader(conn)
	req, err := http.ReadRequest(br)
	if err != nil {
		return
	}
	if req.Method != http.MethodConnect {
		reply(conn, 405, "only CONNECT")
		return
	}
	if subtle.ConstantTimeCompare([]byte(req.Header.Get("Proxy-Authorization")), []byte(want)) != 1 {
		io.WriteString(conn, "HTTP/1.1 407 Proxy Authentication Required\r\n"+
			"Proxy-Authenticate: Basic realm=\"hermes\"\r\nContent-Length: 0\r\n\r\n")
		return
	}
	host, port, err := net.SplitHostPort(req.Host)
	if err != nil {
		reply(conn, 400, err.Error())
		return
	}
	target, err := tailnetTarget(c, host)
	if err != nil {
		reply(conn, 403, err.Error())
		return
	}
	ctx, cancel := context.WithTimeout(context.Background(), 20*time.Second)
	up, err := s.Dial(ctx, "tcp", net.JoinHostPort(target, port))
	cancel()
	if err != nil {
		reply(conn, 502, err.Error())
		return
	}
	defer up.Close()
	conn.SetReadDeadline(time.Time{})
	if _, err := io.WriteString(conn, "HTTP/1.1 200 Connection Established\r\n\r\n"); err != nil {
		return
	}
	done := make(chan struct{}, 2)
	go func() {
		io.Copy(up, br) // br first hands over anything read past the CONNECT header
		done <- struct{}{}
	}()
	go func() {
		io.Copy(conn, up)
		done <- struct{}{}
	}()
	<-done // either side ended; the deferred closes end the other
}

// tailnetTarget is the address to dial for host: a Tailscale IP as is, or a
// MagicDNS name (…ts.net) resolved from the tailnet's own device list.
func tailnetTarget(c *local.Client, host string) (string, error) {
	if ip, err := netip.ParseAddr(host); err == nil {
		if !tsaddr.IsTailscaleIP(ip) {
			return "", fmt.Errorf("%s is not a Tailscale address", host)
		}
		return host, nil
	}
	name := strings.TrimSuffix(strings.ToLower(host), ".")
	if !strings.HasSuffix(name, ".ts.net") {
		return "", fmt.Errorf("%s is not a Tailscale name", host)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	st, err := c.Status(ctx)
	if err != nil {
		return "", err
	}
	for _, p := range st.Peer {
		if strings.EqualFold(strings.TrimSuffix(p.DNSName, "."), name) && len(p.TailscaleIPs) > 0 {
			return p.TailscaleIPs[0].String(), nil
		}
	}
	return "", fmt.Errorf("no device named %s in this tailnet", name)
}

func reply(conn net.Conn, code int, msg string) {
	fmt.Fprintf(conn, "HTTP/1.1 %d %s\r\nContent-Type: text/plain\r\nContent-Length: %d\r\n\r\n%s",
		code, http.StatusText(code), len(msg), msg)
}

type addrJSON struct {
	IP     string `json:"ip"`
	Prefix int    `json:"prefix"`
}

type ifaceJSON struct {
	Name      string     `json:"name"`
	Index     int        `json:"index"`
	MTU       int        `json:"mtu"`
	Up        bool       `json:"up"`
	Loopback  bool       `json:"loopback"`
	P2P       bool       `json:"p2p"`
	Multicast bool       `json:"multicast"`
	Addrs     []addrJSON `json:"addrs"`
}

func interfaces() ([]netmon.Interface, error) {
	p := currentPlatform()
	if p == nil {
		return nil, errors.New("tsbridge: no platform")
	}
	var in []ifaceJSON
	if err := json.Unmarshal([]byte(p.InterfacesJSON()), &in); err != nil {
		return nil, err
	}
	out := make([]netmon.Interface, 0, len(in))
	for _, it := range in {
		if it.Name == "" {
			continue
		}
		nif := netmon.Interface{
			Interface: &net.Interface{Name: it.Name, Index: it.Index, MTU: it.MTU},
			// Non-nil, so Addrs() uses these instead of asking the kernel.
			AltAddrs: []net.Addr{},
		}
		if it.Up {
			nif.Flags |= net.FlagUp
		}
		if it.Loopback {
			nif.Flags |= net.FlagLoopback
		}
		if it.P2P {
			nif.Flags |= net.FlagPointToPoint
		}
		if it.Multicast {
			nif.Flags |= net.FlagMulticast | net.FlagBroadcast
		}
		for _, a := range it.Addrs {
			ip, err := netip.ParseAddr(a.IP)
			if err != nil {
				continue
			}
			if ip.Zone() != "" {
				nif.AltAddrs = append(nif.AltAddrs, &net.IPAddr{IP: ip.AsSlice(), Zone: ip.Zone()})
				continue
			}
			bits := ip.BitLen()
			if a.Prefix < 0 || a.Prefix > bits {
				a.Prefix = bits
			}
			nif.AltAddrs = append(nif.AltAddrs, &net.IPNet{IP: ip.AsSlice(), Mask: net.CIDRMask(a.Prefix, bits)})
		}
		out = append(out, nif)
	}
	return out, nil
}

// recovered turns a panic in a call from Kotlin into an error, so a bug here
// shows up in the app instead of killing it.
func recovered(err *error) {
	if r := recover(); r != nil {
		logf("tsbridge: panic: %v\n%s", r, debug.Stack())
		*err = fmt.Errorf("panic: %v", r)
	}
}

func recoveredJSON(ret *string) {
	if r := recover(); r != nil {
		logf("tsbridge: panic: %v\n%s", r, debug.Stack())
		*ret = toJSON(map[string]string{"state": "Error", "error": fmt.Sprintf("panic: %v", r)})
	}
}

const maxLogLines = 1500

var (
	logMu    sync.Mutex
	logLines []string
)

func logf(format string, args ...any) {
	line := time.Now().Format("15:04:05 ") + strings.TrimRight(fmt.Sprintf(format, args...), "\n")
	logMu.Lock()
	logLines = append(logLines, line)
	if len(logLines) > maxLogLines {
		logLines = slices.Clone(logLines[len(logLines)-maxLogLines:])
	}
	logMu.Unlock()
}

// Logs returns the newest log lines, oldest first.
func Logs() string {
	logMu.Lock()
	defer logMu.Unlock()
	return strings.Join(logLines, "\n")
}

func toJSON(v any) string {
	b, err := json.Marshal(v)
	if err != nil {
		return `{"error":"json"}`
	}
	return string(b)
}

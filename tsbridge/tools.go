//go:build tools

package tsbridge

// gomobile bind needs golang.org/x/mobile/bind in the module graph.
import _ "golang.org/x/mobile/bind"

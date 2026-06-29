# Vev Odin

Vev is authored in Kvist and lowers through Odin, but generated Odin is build
output, not the public Odin package surface.

For Odin applications, the supported direction is a small wrapper over the C
ABI:

- link against the platform library under `build/lib`
- import functions matching `include/vev.h`
- manage Vev handles explicitly, mirroring the C ABI ownership rules

A future `clients/odin` package can wrap those foreign imports with Odin-native
types. Directly depending on generated Odin should remain a development/debug
escape hatch rather than the documented integration path.

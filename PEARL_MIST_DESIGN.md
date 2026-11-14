# Prism Browser — Pearl Mist liquid glass

The uploaded views-rewrite source is the base. The shared `Kit.kt` palette and material now use Fast Score-inspired Pearl Mist: pale sage base, pink/blue/lilac radial glows, translucent white glass, bright rim, subtle bevel and softer elevation. AMOLED dark background is black.

All existing phone and tablet controls that call `glass()` inherit the updated material without changing browser navigation or functionality. The wallpaper is static and does not add per-frame rendering.

This is a visual implementation, not a byte-for-byte copy of Fast Score's React Native glass shaders. Build and test on hardware before publishing.

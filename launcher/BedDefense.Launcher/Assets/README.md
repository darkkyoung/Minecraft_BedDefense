# L3 original launcher assets

`BedDefense.svg` is an original vector drawing of a red block bed, white pillow and
wooden frame on a deep teal tile. `Brand.png` is its 256 px render; `BedDefense.ico`
contains 16, 24, 32, 48, 64, 128 and 256 px frames. The SVG is the editable source.
No official logo or downloaded Minecraft asset is included.

`BedDefense-Hero.jpg` is original AI-generated launcher artwork, made with the
built-in image generation tool for this project on 2026-10-19. The generated image
was encoded as JPEG (quality 88, optimized) without changing the composition;
the embedded 2048×683 image is about 294 KiB. No runtime network fetch is needed.
The same artwork sits in a restrained frame in both themes; its scene colors do
not change with the theme. WPF crops responsively and uses high-quality scaling.

Generation prompt:

> Use case: stylized-concept. Asset type: original wide hero banner for the Bed Defense Windows game launcher. Create a polished Minecraft-inspired block-world illustration: a clearly visible red bed with white pillow on a grassy stone platform at the center, a blocky adventurer with a sword and wooden shield standing protectively beside it, while a few blocky green zombie-like monsters and one skeletal archer approach from the dark forest edges. Cozy but dangerous: warm lantern glow around the bed contrasts with deep teal twilight forest and navy distant hills. Sophisticated stylized 3D illustration, clean broad geometric forms, subtle texture, crisp silhouettes, restrained detail. Landscape panoramic composition approximately 3:1, central subjects large and clearly legible when displayed in a shallow launcher banner; keep the bed and defender wholly within the middle horizontal band, plenty of environmental breathing room left and right. No text, no letters, no logos, no watermark, no UI, no official Minecraft artwork copied, no photorealism, no neon. Premium indie game key art, red bedding and warm gold highlights as the focal point.

The generated PNG remains the source outside the runtime package. This banner is
an illustration, not a gameplay screenshot. UI acceptance images in docs and CI
are actual WPF renders and are separate from the artwork.

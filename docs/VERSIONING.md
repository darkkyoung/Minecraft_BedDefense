# Version policy

- `main` initially targets **Minecraft Java 26.3** (Fabric Loader 0.19.5+, Fabric API 26.3, Java 25).
- Do not claim binary compatibility with other Minecraft versions.
- When requested, branch as `mc/<version>`; port code, run CI, and test mechanics in-game independently.
- Decide later whether `main` should remain pinned at 26.3 or track latest support.
- CI artifacts are ephemeral test outputs; official releases should be tagged and separately validated.

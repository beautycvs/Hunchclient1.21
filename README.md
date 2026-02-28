<h1 align="center">HunchClient</h1>

<p align="center">A Minecraft 1.21.10 Fabric client mod focused on Hypixel Skyblock Dungeons QOL.</p>

<div align="center">
  
[![Discord](https://img.shields.io/discord/1428842479087779934?label=Discord&logo=discord&color=5865F2&style=for-the-badge)](https://discord.gg/hunchclient)
![Badge](https://img.shields.io/badge/Version-1.0.0-orange?style=for-the-badge)
![Badge](https://img.shields.io/badge/Loader-Fabric-brightgreen?style=for-the-badge)

## Installation

</div>

### Requirements
- Java 21 installed
- Minecraft 1.21.10
- [Fabric Loader](https://fabricmc.net/use/) 0.17.3+
- [Fabric API](https://modrinth.com/mod/fabric-api) 0.138.3+1.21.10
- [Fabric Language Kotlin](https://modrinth.com/mod/fabric-language-kotlin) 1.13.2+

### Setup
1. Create a Fabric Loader 1.21.10 instance
2. Drop the required mods into `.minecraft/mods/`
3. Drop `hunchclient-1.0.0-1.21.10.jar` into `.minecraft/mods/`
4. Launch the game 
> [!NOTE]
> You can find the hunchclient .jar file in the actions tab at the top left. 
<br>Click on the latest workflow and download the artifacts; the .jar file will be inside the zip folder.

> <b>Right Shift</b> to open the GUI, this can be changed in minecraft's "Controls" tab.
> <br>HUD elements can be changed via "/hudedit" in chat.
<h1 align="center">Features</h1>

### Dungeons
- **Terminal Solver** - Auto-solves F7/M7 terminals 
- **Secret Routes** - Pathfinding waypoints for dungeon secrets with etherwarp points and TNT locations
- **Secret Waypoints** - Highlights all secret locations 
- **Secret Triggerbot & Aura** - Auto-interact with nearby secrets
- **Dungeon Map** - Skeet-styled minimap with player heads and room detection
- **Starred Mobs ESP** - Highlights starred mobs through walls
- **Align Aura** 
- **Auto Superboom** - Automatic superboom TNT
- **Auto Mask Swap** - Swaps masks automatically during boss fights
- **Close Dungeon Chests** - Auto-closes reward chests
- **Chest Aura** - Highbounce helper


### F7/M7 Boss
- **Bonzo Staff Helper** - Backward press and velocity cancel for Bonzo staff jumps
- **Auto SS** - Automatic Simon Says

### Party & Social
- **Auto Kick** - Kick players below stat thresholds
- **Custom Leap Message** - Quick leap to party members by name
- **IRC Chat** - Built-in IRC chat system with Discord bridge
- **OG List** - See whos OG
- **Name Protect** - Protects ur Name

### Visuals
- **Dark Mode Shader** - Full post-processing dark mode
- **Custom Animations** - 1.7 swing, block hit, custom speed
- **Custom Font** - NanoVG-based font rendering with TTF support
- **Etherwarp ESP** - Glow shader on etherwarp-able blocks
- **Custom Mage Beam** - Customizable mage beam rendering
- **Player Size/Spin** - Resize and spin player models
- **Stretch Resolution** - Custom resolution scaling
- **Skeet Theme** - Customizable GUI colors

### Rendering
- **Full Bright** - Maximum brightness
- **Remove Armor** - Hide armor on players
- **Hide Particles** - Remove explosion/block break particles
- **Disable Overlays** - Remove fire, water, pumpkin, vignette overlays
- **Disable Fog** - Remove distance fog
- **Custom Hit Sound** - Replace hit sounds with custom .ogg files

### Utilities
- **Freecam** - Free-floating spectator camera
- **Blink** - Queue outgoing packets for teleport effect (Bannable)
- **FakeLag** - Delay packets (Clumsy) (Bannable)
- **Replay Buffer** - FFmpeg-based instant replay recording
- **Chat Utils** - Copy messages, clickable links, timestamps
- **Kaomoji Replacer** - Replace text with kaomoji faces
- **Config Import/Export** - Save and load module configurations

### F7 Terminal Simulator
- **F7Sim** - Practice terminals locally with simulated armor stands
- **Ivor Fohr** - Full device simulation for i4

## Commands

| Command | Description |
|---------|-------------|
| `.hc <module>` | Toggle modules |
| `.hc bind <module> <key>` | Bind module to key |
| `/irc [message]` | Toggle IRC mode or send message |
| `/hcc <message>` | Send IRC message |
| `/f7debug` | Show current F7 phase info |
| `/hudeditor` | Open HUD element editor |

## Building from Source

```bash
git clone https://github.com/hunchsss/Hunchclient1.21.git
cd Hunchclient1.21
./gradlew build
```

The output JAR will be in `build/libs/`.

### Build Dependencies
- Java 21 (Eclipse Temurin recommended)
- Gradle (wrapper included)

### Runtime Dependencies (bundled)
- [Meteor Orbit](https://github.com/MeteorDevelopment/orbit) 0.2.3 - Event bus
- [LWJGL NanoVG](https://www.lwjgl.org/) 3.3.3 - Vector graphics rendering
- [JNA](https://github.com/java-native-access/jna) 5.14.0 - Native access (file dialogs, media control)

## License

MIT - Free to use, modify and distribute with credit.

## Discord

Join us: [discord.gg/hunchclient](https://discord.gg/hunchclient) 

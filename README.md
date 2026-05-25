Stones Mod

A Minecraft Forge mod for version 1.20.1.

Stones is a passion project that tackles a decade-old design problem in Minecraft: the Experience system. Because enchanting turns XP into a disposable resource (meaning a player's level constantly drops and rises), implementing a traditional skill tree was never really viable.

Stones solves this by introducing an item-based skill tree that is persistent, yet entirely built by the player. Instead of a static UI menu, you bind yourself to physically generated monoliths in the world and socket items (runes) into them. This bypasses the fluctuating XP problem while keeping the progression dynamic and entirely in your hands!

🌟 Features

🔮 The Void Altars: Find dormant, black monoliths in the world and bind your soul to them through a short ritual. Each altar generates a mathematically completely unique socket layout (a Phyllotaxis spiral) based on its ID. Your active rune setup and your binding remain completely intact even after death.

🪨 The Rune System: Personalize your altar with three different types of runes:

Minor Runes: Provide flat, passive bonuses.

Major Runes: Scale directly with your current player level.

Milestone Runes: Grant entirely new, active abilities. Their strength is based on their enchantments (which can scale well beyond standard Vanilla limits).

Cluster Jewels: Out of space in your altar? Bundle up to five runes and insert them into a single socket!

Meaningful Curses: Vanilla curses (like Curse of Binding) drastically lower the level requirement of a rune but come with dangerous side effects. (Warning: Attempting to mine a Void Altar that contains a rune with the Curse of Binding will result in a catastrophic explosion!)

⚔️ Clean Action Bar: No more cluttered hotkeys! Stones uses a minimalist 3-button philosophy (Default: R, G, and V). Your active skills are assigned to a new action bar next to your hotbar with clear cooldown tracking.

👻 The Echo Trader: An ethereal wanderer whose currency is your XP. He sells runes, Vanilla items, and Resonance Boxes. Brave players can also offer him a "Blood Sacrifice" (max HP) in exchange for massive XP boosts—but overestimating your health will result in instant death.

💀 Death as a Feature: Dying is no longer a pure Game Over! Your classic Vanilla "Death Score" (the yellow number shown on the death screen) is repurposed and placed on a server-wide leaderboard. A good run rewards you with Resonance Boxes upon respawning. These boxes dynamically combine Vanilla loot with runes. From Tier 10 onwards, they even pull items from all installed mods and can generate equipment with enchantments up to level 100!

⚙️ 100% Data-Driven (For Modders): The entire engine is data-driven. Modpack creators can create new runes and abilities or adjust the trader's loot tables using simple JSON files.

🔗 Links & Support

📖 Official Wiki: n3g.de/wiki/en/stones

🎥 Showcase Video (German Audio): Watch on YouTube

☕ Support the Project: Ko-fi / KarlKarlmann

🎮 Installation (For Players)

💡 Note: You can always download the latest compiled version directly from our CurseForge page.

Make sure Minecraft Forge for version 1.20.1 is installed.

Download the latest .jar file of the mod.

Place the file into your Minecraft mods folder.

Start the game!

💻 Development (Build from Source)

If you want to view the code or compile the mod yourself:

Prerequisites

Java 17

Git

Setup & Build

Clone this repository:

git clone [https://github.com/KarlKarlmann/stones.git](https://github.com/KarlKarlmann/stones.git)


Change into the directory:

cd stones


Run the Gradle build:

# On Windows
gradlew build

# On Linux/Mac
./gradlew build


The compiled mod can be found in the build/libs/ folder.

Developer Note: This project uses a custom Gradle script that automatically pushes changes to the main branch after a successful build whenever the version in gradle.properties is incremented.

📜 Credits & Permissions

Author: KarlKarlmann

Modpacks: You are free to include this mod in any CurseForge modpack. Modpack creators are welcome to adjust rune effects and skills via JSON datapacks to fit their intended progression!

Videos & Streams: You are absolutely welcome to use this mod in your YouTube videos or Twitch streams.

Re-Uploads: Please do not re-upload the mod files to other websites. Always link back to the original CurseForge/GitHub page.

⚖️ License

This project is licensed under the CC BY-NC 4.0 License. Find more details at Creative Commons.
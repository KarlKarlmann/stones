# Stones Iron's Spells 'n Spellbooks Bridge

![Minecraft Version](https://img.shields.io/badge/Minecraft-1.20.1-brightgreen)
![Forge Version](https://img.shields.io/badge/Forge-47.2.0+-orange)
![License](https://img.shields.io/badge/License-MIT-blue)
[![Support on Ko-fi](https://img.shields.io/badge/Ko--fi-Support_Project-F16061?logo=ko-fi&logoColor=white)](https://ko-fi.com/karlkarlmann)

A Minecraft Forge mod for version 1.20.1.

**Stones Iron's Spellbooks Bridge** is the official compatibility and integration bridge that fuses two of the most immersive RPG systems in Minecraft: the tactical combat action and minimalist UI philosophy of the **Stones Mod**, and the deep, expansive arcane sandbox of **Iron's Spells & Spellbooks**.

This mod bridges the gap between isolated combat mechanics, fully embedding spellcasting into the core progression loop of Stones. Magic is no longer an independent sideshow—it becomes a high-stakes, deeply rewarding aspect of your character's journey.

## 🌟 Features

* **🔮 Dynamic Knowledge System:** Spells are no longer gated behind arbitrary level caps. The bridge scans all active mods and addons at startup to dynamically register a Forge attribute (e.g., `knowledge_blood` or `knowledge_fire`) for every single magic school. Requirements grow progressively following a quadratic curve: Required Knowledge = 2.04 * (Effective Circle - 1)^2 (Circle 8 corresponds to exactly 100.0 knowledge points).

* **🎲 Arcane Backlash & Wild Magic:** Casting spells beyond your current understanding is a dangerous gamble. Every point of missing knowledge reduces your success rate by -2.5%. The Luck attribute can aid you, but it strictly caps out at an 85% success chance—true mastery cannot be cheated. A fatal miscast on high-tier spells Sample inflicts severe punishments like Confusion, Weakness, and Blindness, or unleashes an uncontrollable *Wild Magic Surge*, casting a random spell from the chaos pool entirely for free!

* **🧪 Reagent Economy & Trade Integration:** Spells demand sacrifices! Many incantations now consume physical components (e.g., Ender Pearls for Ender magic or Gunpowder for Fire magic). To support this newly introduced economy, the Wandering Trader now buys your excess resources (Dirt, Cobblestone, Deepslate, Sand) for Emeralds and sells the critical reagents required to fuel your spells.

* **⚔️ Seamless Action UI Injection:** No more cluttered hotkeys or multi-keybind paralysis! Your unlocked spells are automatically injected directly into the cache of the Stones Action System. The spell cooldowns are accurately calculated and translated from internal game ticks into readable seconds on your action bar.

* **📖 Inscription Table Overhaul & Tooltips:** A complete Mixin overhaul replaces hard-to-read interfaces. The Inscription Table renders circles, required knowledge, mana costs, and cast times in an elegant book-brown (`0x322C2A`) instead of the unreadable vanilla white. Furthermore, selecting the Stones layout automatically unbinds conflicting default Iron's Spells keys to guarantee a smooth setup.

## 🛠️ Mod Status Effects (Buffs)

The bridge introduces three powerful status effects that can be perfectly implemented as milestone rewards within your Stones configuration:
* **Arcane Overdrive (`overdrive`):** Massively reduces cast time, sets mana cost to zero, and skips reagent requirements for the next spellcast. The buff is consumed upon use.
* **Clearcast (`clearcast`):** Grants a short, volatile window where all your spells can be cast without consuming physical reagents from your inventory.
* **Quickcast (`quickcast`):** Instantly resets the cooldown of the cast spell via a delayed server-work tick right after completion.

## ⚙️ Configuration (For Modpack Creators)

Full control over the integration is generated inside the `config/stones_irons_bridge/` directory:
* `server_settings.json`: Toggles server-wide rules, such as global reagent consumption.
* `client_settings.json`: Stores the user's interface preferences (Stones UI vs. Iron's UI).
* `reagent_list.json`: Allows server administrators to completely re-program the circle, reagent items, and required counts for every single spell (including addon spells) in the game.

## 🔗 Links & Support

* ☕ **Support the Project:** [Ko-fi / KarlKarlmann](https://ko-fi.com/karlkarlmann)

## 🎮 Installation (For Players)

1. Make sure Minecraft Forge for version 1.20.1 is installed.
2. Download the latest `.jar` file of the mod.
3. Place the file into your Minecraft `mods` folder along with **Stones** and **Iron's Spells & Spellbooks**.
4. Start the game!

## 💻 Development (Build from Source)

### Prerequisites
* Java 17
* Git

### Setup & Build
1. Clone this repository into your local workspace.
2. Create a folder named `libs/` in the project root directory.
3. Place the deobfuscated `.jar` files of the **Stones Mod** and **Iron's Spells & Spellbooks** inside the `libs/` folder.
4. Adjust the version identifiers in `gradle.properties` to match your local files if necessary.
5. Run the Gradle build (uncomment the matching command for your OS):
```bash
# On Windows
gradlew build

# On Linux/Mac
./gradlew build
```

## ⚖️ License

This project is licensed under the **CC BY-NC 4.0** License. Find more details at [Creative Commons](https://creativecommons.org/licenses/by-nc/4.0/).

---
**Author:** KarlKarlmann  
*Special thanks to the developers of Stones and Iron's Spells & Spellbooks for providing wonderful systems to build upon!*
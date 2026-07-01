# Helpful Clerics

Helpful Clerics is a Minecraft Fabric mod that gives cleric villagers a useful support behavior: they can heal damaged villagers.

When a cleric finds a nearby injured villager, it approaches while holding a healing potion and applies Instant Healing. Clerics avoid healing during dangerous situations, at night, or after taking damage.

## Features

| Feature                  | Description                                                  |
| ------------------------ | ------------------------------------------------------------ |
| Villager Healing         | Cleric villagers can heal nearby damaged villagers.          |
| Healing Potion Animation | Clerics hold a potion while going to heal a villager.        |
| Healing Feedback         | Healing uses happy villager particles and a magic sound.     |
| Group Splash Healing     | Clerics throw splash healing when multiple villagers are hurt.|
| Danger Awareness         | Outside raids, clerics avoid healing near hostile mobs.      |
| Raid Support             | During raids, clerics keep healing instead of going to sleep.|
| Self-Preservation        | Injured clerics heal themselves before helping others.       |
| Patient Seeking          | Injured villagers run directly to a cleric for healing.      |
| Night Safety             | Clerics do not risk healing villagers at night.              |
| Vanilla-Friendly         | Keeps the behavior simple and close to vanilla Minecraft.    |
| Lightweight              | Focused only on improving cleric and villager interaction.   |

## How It Works

If there is a damaged villager nearby, a cleric will try to approach it.

The cleric prioritizes the most injured visible villager first. It refreshes its search every 5 seconds so newly injured villagers are picked up while it is already helping others.

When it can reach and see the patient, it holds a healing potion and applies Instant Healing directly.

If two or more visible injured villagers are close to a cleric, it throws a splash Instant Healing potion at the most injured one.

If the selected injured villager is visible but unreachable, the cleric also throws a splash Instant Healing potion at that villager as a fallback.

When healing happens, happy villager particles appear around the healed villager and a magic sound is played.

If a villager is hurt, it runs directly toward the nearest cleric. Once healed, this mod stops controlling its movement so vanilla villager AI can resume its normal task.

If no cleric is available, injured villagers keep their normal vanilla behavior.

If a cleric is hurt, it prioritizes healing itself before healing other villagers.

Outside raids, if hostile mobs are nearby or if it is night, the healing behavior is cancelled.

During a raid, clerics ignore the night/rest restriction and constantly scan for injured villagers. They still prioritize their own health before helping anyone else.

If raiders are too close during a raid, clerics do not walk toward distant patients. They only heal villagers already within reach or throw splash healing from their current position if the patient is visible.

## Behavior Details

* Search radius: **16 blocks**
* Heal range: **2.4 blocks**
* Group splash radius: **4 blocks**
* Group splash threshold: **2 visible injured villagers**
* Injured villager seek range: **16 blocks**
* Patient detection: **requires line of sight**
* Patient priority: **lowest visible health percentage first**
* Forced patient refresh: **every 5 seconds**
* Raid patient refresh: **constant while a raid is active**
* Cleric self-heal delay: **1 second**
* Heal cooldown: **3 seconds**
* Direct healing: **requires line of sight**
* Splash potion: **used for 2+ nearby visible injured villagers, or when the selected visible patient cannot be reached**
* Raid behavior: **ignores night/rest, self-heals first, and avoids walking toward danger**
* No cleric nearby: **injured villagers keep vanilla behavior**

## Compatibility

* Minecraft: **1.21.1 through 1.21.11 and 26.1 through 26.2**
* Mod loader: **Fabric**
* Environment: **Client and Server**

## Support

Need help, found a bug, or want to suggest something?

Join the Discord support forum:

https://discord.gg/pcKQSDgTzh

## License

This project is licensed under the MIT License.

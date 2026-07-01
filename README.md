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

Clerics act like village support units.

When a villager gets hurt, it runs toward the nearest cleric instead of fleeing randomly. If no cleric is nearby, vanilla behavior is left unchanged.

The cleric looks for visible injured villagers, prioritizes the most injured one, walks to it, holds an Instant Healing potion, and heals it directly.

If multiple injured villagers are close together, or if the target is visible but unreachable, the cleric throws a splash Instant Healing potion instead.

Healing creates happy villager particles and plays a magic sound. After being healed, villagers return to normal vanilla AI unless there is still danger nearby.

Clerics are not reckless. Outside raids, they avoid healing at night or near hostile mobs. During raids, they keep searching for injured villagers, but still heal themselves first and avoid walking into obvious danger.

## Behavior Details

| Setting                 | Value                                      |
| ----------------------- | ------------------------------------------ |
| Search radius           | **16 blocks**                              |
| Heal range              | **2.4 blocks**                             |
| Injured villager range  | **16 blocks**                              |
| Group splash trigger    | **2 visible injured villagers**            |
| Group splash radius     | **4 blocks**                               |
| Patient priority        | **Most injured visible villager first**    |
| Patient refresh         | **Every 5 seconds, constant during raids** |
| Cleric self-heal delay  | **1 second**                               |
| Heal cooldown           | **3 seconds**                              |
| Visibility rule         | **Healing requires line of sight**         |

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

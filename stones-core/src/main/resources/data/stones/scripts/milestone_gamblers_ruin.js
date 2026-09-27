global.Stones.register('ON_ACTION_BUTTON', 'milestone_gamblers_ruin', ctx => {
    const player = ctx.player;
    if (!player || !global.Stones.isReady(player, 'milestone_gamblers_ruin')) return;

    const mainHand = player.mainHandItem;

    // 1. Voraussetzung: Das getragene Item muss verzaubert sein
    if (!mainHand || mainHand.isEmpty() || !mainHand.isEnchanted()) {
        global.Stones.playSound(player, 'minecraft:entity.villager.no', 'players', 1.0, 0.8);
        player.tell('§c[Gambler\'s Ruin] You need an enchanted item in your hand as a stake!');
        return;
    }

    // 2. Opfergabe: Zufällige Verzauberung abziehen und Cooldown setzen
    const sacPow = Number(global.Stones.removeRandomEnchantment(player) || 1);
    global.Stones.setCooldown(player, 'milestone_gamblers_ruin', 400); // 20s Cooldown für HUD-Sync

    const level = player.level;
    const pos = player.position();
    
    // 3. Ergebnis auswürfeln (0 bis 4)
    const outcome = Math.floor(Math.random() * 5);

    switch (outcome) {

        case 0: {
            mainHand.enchant('minecraft:mending', 1);
            mainHand.enchant('minecraft:unbreaking', 3);

            global.Stones.playSound(player, 'minecraft:ui.toast.challenge_complete', 'players', 1.0, 1.0);
            global.Stones.spawnParticles(player, 'minecraft:totem_of_undying', pos.add(0, 1, 0), 60, 0.5, 0.8, 0.5, 0.15);
            player.tell('§6★ WIN! The sacrifice blessed your item with Mending & Unbreaking III!');
            break;
        }

        case 1: {
            player.potionEffects.add('minecraft:resistance', 3600, 1);
            player.potionEffects.add('minecraft:strength', 3600, 1);

            global.Stones.playSound(player, 'minecraft:entity.zombie_villager.cure', 'players', 1.0, 1.2);
            global.Stones.spawnParticles(player, 'minecraft:witch', pos.add(0, 1, 0), 40, 0.5, 1.0, 0.5, 0.05);
            player.tell('§b✦ BLESSING! The sacrificed magic surges through your body for 3 minutes!');
            break;
        }

        case 2: {
            if (level) {
                let wolf = level.createEntity('wolf');
                wolf.setPosition(player.x + 1.0, player.y, player.z + 1.0);
                wolf.mergeNbt({
                    Owner: String(player.getUuid()),
                    Tame: true,
                    CustomName: '{"text":"Shard Hound"}',
                    CustomNameVisible: true,
                    Glowing: true,
                    CollarColor: 10
                });
                wolf.setAttributeBaseValue('generic.max_health', 40.0);
                wolf.setHealth(40.0);
                wolf.spawn();
            }

            global.Stones.playSound(player, 'minecraft:entity.wolf.howl', 'players', 1.0, 1.0);
            global.Stones.spawnParticles(player, 'minecraft:enchant', pos.add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
            player.tell('§d✦ LOYAL COMPANION! A magical Shard Hound awakes from the magic!');
            break;
        }

        case 3: {
            if (level) {
                let zombie = level.createEntity('zombie');
                zombie.setPosition(player.x + 1.5, player.y, player.z + 1.5);
                
                zombie.mergeNbt({
                    CustomName: '{"text":"§cWrath of Sacrificed Magic"}',
                    CustomNameVisible: true,
                    Glowing: true,
                    HandItems: [
                        {
                            id: "minecraft:netherite_sword",
                            Count: 1,
                            tag: {
                                Enchantments: [{ id: "minecraft:sharpness", lvl: 4 }]
                            }
                        },
                        {}
                    ],
                    ArmorDropChances: [0.0, 0.0, 0.0, 0.0]
                });
                zombie.spawn();
                zombie.setTarget(player);
            }

            global.Stones.playSound(player, 'minecraft:entity.wither.spawn', 'hostile', 0.6, 1.4);
            global.Stones.spawnParticles(player, 'minecraft:flame', pos.add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);
            player.tell('§c⚠️ BACKFIRE! The sacrificed magic turned into a hostile monster!');
            break;
        }

        case 4: {
            mainHand.shrink(1);

            global.Stones.explode(player, pos, 4.0, false);
            global.Stones.playSound(player, 'minecraft:entity.generic.explode', 'players', 1.0, 0.7);
            global.Stones.spawnParticles(player, 'minecraft:explosion_emitter', pos.add(0, 1, 0), 1, 0, 0, 0, 0);

            player.tell('§4☠ TOTAL DESTRUCTION! The item exploded right in your hands!');
            break;
        }
    }
});
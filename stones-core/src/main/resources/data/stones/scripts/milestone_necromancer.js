function getNecroStats(player) {
    let data = global.Stones.getRuneData(player, 'stones:milestone_necromancer');
    let rLvl = Math.max(1, Number(data.runeLevel || 1));
    let sLvl = Math.max(1, Number(data.socketLevel || 1));
    return {
        boneDamage: 4.0 + (0.8 * (rLvl - 1)),
        maxBones: Math.min(8, Math.floor(3.0 + (0.25 * (sLvl - 1)))),
        absorbHeal: 2.0 + (0.3 * (rLvl - 1))
    };
}

global.Stones.register('ON_KILL', 'milestone_necromancer', ctx => {
    const player = ctx.player;
    if (!player) return;

    let stats = getNecroStats(player);
    let currentBones = Number(global.Stones.getComboCount(player, 'necromancer_bones') || 0);
    let newBones = Math.min(stats.maxBones, currentBones + 1);

    global.Stones.updateCombo(player, 'necromancer_bones', newBones, stats.maxBones, 'minecraft:textures/item/bone.png', 0.6, 1.5, 2.5, '#EAE0D0', 1200);

    global.Stones.playSound(player, 'minecraft:entity.skeleton.ambient', 'players', 0.8, 1.2);
    global.Stones.spawnParticles(player, 'minecraft:soul', player.position().add(0, 1, 0), 15, 0.4, 0.4, 0.4, 0.05);
});

global.Stones.register('ON_HURT', 'milestone_necromancer', ctx => {
    const player = ctx.player;
    if (!player) return;

    let currentBones = Number(global.Stones.getComboCount(player, 'necromancer_bones') || 0);
    if (currentBones <= 0) return;

    let stats = getNecroStats(player);
    let newBones = currentBones - 1;
    global.Stones.updateCombo(player, 'necromancer_bones', newBones, stats.maxBones, 'minecraft:textures/item/bone.png', 0.6, 1.5, 2.5, '#EAE0D0', 1200);

    player.potionEffects.add('minecraft:resistance', 20, 2);
    player.potionEffects.add('minecraft:absorption', 100, 1);
    player.heal(stats.absorbHeal);

    const pos = player.position();
    global.Stones.playSound(player, 'minecraft:entity.skeleton.hurt', 'players', 1.0, 0.6);
    global.Stones.spawnParticles(player, 'minecraft:soul_fire_flame', pos.add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.15);
});

global.Stones.register('ON_ACTION_BUTTON', 'milestone_necromancer', ctx => {
    const player = ctx.player;
    if (!player || !global.Stones.isReady(player, 'milestone_necromancer')) return;

    let bones = Number(global.Stones.getComboCount(player, 'necromancer_bones') || 0);
    if (bones <= 0) {
        global.Stones.playSound(player, 'minecraft:entity.villager.no', 'players', 1.0, 0.8);
        player.tell('§c[Necromancer] You need at least 1 Bone Charge from kills to activate Bone Explosion!');
        return;
    }

    global.Stones.setCooldown(player, 'milestone_necromancer', 400);

    let stats = getNecroStats(player);
    const level = player.level;
    const pos = player.position();

    const offsetsX = [1.0, -1.0, 1.5, -1.5, 2.0, -2.0, 2.5, -2.5];
    const offsetsZ = [0.0,  0.5, -0.5,  1.0, -1.0,  1.5, -1.5,  2.0];

    let minionCount = Math.max(1, Math.floor(bones / 2));
    let totalDamage = bones * stats.boneDamage;

    if (level) {
        let targets = global.Stones.findEntities(player, 'RADIUS', 8, 8, 8, 8, true, true, false);
        if (targets) {
            targets.forEach(e => {
                if (!e.tags.contains('stones_minion')) {
                    e.attack(totalDamage);
                }
            });
        }

        for (let i = 0; i < minionCount; i++) {
            let skel = level.createEntity('wither_skeleton');
            let offsetX = offsetsX[i % offsetsX.length];
            let offsetZ = offsetsZ[i % offsetsZ.length];

            skel.setPosition(player.x + offsetX, player.y + 0.2, player.z + offsetZ);
            skel.spawn();

            skel.setMainHandItem('minecraft:netherite_sword');
            global.Stones.makeMinion(skel, player);
        }
    }

    global.Stones.playSound(player, 'minecraft:entity.wither.spawn', 'hostile', 0.9, 1.2);
    global.Stones.playSound(player, 'minecraft:entity.wither_skeleton.death', 'hostile', 1.0, 0.7);
    global.Stones.spawnParticles(player, 'minecraft:soul_fire_flame', pos.add(0, 1, 0), 100, 1.5, 1.0, 1.5, 0.2);

    global.Stones.updateCombo(player, 'necromancer_bones', 0, stats.maxBones, '', 0, 0, 0, '#000000', 0);
    player.tell(`§b✦ BONE EXPLOSION! Detonated ${bones} Bone Shield(s) for ${totalDamage.toFixed(1)} damage & summoned ${minionCount} Soul Servant(s)!`);
});
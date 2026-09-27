// =============================================================================
// STONES ENGINE - KUBEJS RUNTIME HELPER & TRIGGER HANDLER
// =============================================================================

const StonesBridge = Java.loadClass('net.stones.transpiler.StonesScriptBridge');

// Bei jedem Skript-Reload alte Trigger-Registrierungen in Java säubern
try {
    StonesBridge.clearRegisteredTriggers();
} catch (e) {}

global.Stones = {
    // Verschachtelte Handler-Maps: handlers[triggerName][cleanRuneId] = callback(ctx)
    handlers: {},

    // Universelle Registrierung fuer Transpiler und Mod-Skripte
    register: function(triggerName, runeId, callback) {
        let cleanId = runeId.replace('stones:', '');
        if (!this.handlers[triggerName]) {
            this.handlers[triggerName] = {};
        }
        this.handlers[triggerName][cleanId] = callback;
        this.handlers[triggerName]['stones:' + cleanId] = callback;

        // Java sofort Bescheid geben: Diese Rune hört auf diesen Trigger!
        try {
            StonesBridge.registerRuneTrigger(cleanId, triggerName);
        } catch (e) {}
    },

    registerHandler: function(triggerName, runeId, callback) {
        this.register(triggerName, runeId, callback);
    },

    // Abwaertskompatible Aliase
    onAction: function(runeId, callback) { this.register('ON_ACTION_BUTTON', runeId, callback); },
    onAttack: function(runeId, callback) { this.register('ON_ATTACK', runeId, callback); },
    onHurt: function(runeId, callback) { this.register('ON_HURT', runeId, callback); },
    onKill: function(runeId, callback) { this.register('ON_KILL', runeId, callback); },
    onSwing: function(runeId, callback) { this.register('ON_SWING', runeId, callback); },
    onTick: function(runeId, callback) { this.register('ON_TICK', runeId, callback); },
    onBlockBreak: function(runeId, callback) { this.register('ON_BLOCK_BREAK', runeId, callback); },
    onProjectileHit: function(runeId, callback) { this.register('ON_PROJECTILE_HIT', runeId, callback); },
    onJump: function(runeId, callback) { this.register('ON_JUMP', runeId, callback); },
    onCustom: function(triggerName, runeId, callback) { this.register(triggerName, runeId, callback); },

    // =========================================================================
    // JAVA BRIDGE DELEGATES
    // =========================================================================
    getRuneData: function(entity, runeId) {
        try {
            return StonesBridge.getRuneData(entity, runeId);
        } catch (e) {
            return { runeLevel: 1, socketLevel: 1, mult: 1.0 };
        }
    },

    isReady: function(entity, name) {
        return StonesBridge.isReady(entity, name);
    },

    setCooldown: function(entity, name, ticks) {
        StonesBridge.setCooldown(entity, name, Number(ticks));
    },

    updateCombo: function(entity, id, value, max, texture, size, radius, speed, colorHex, timeout) {
        StonesBridge.updateCombo(entity, id, Number(value), Number(max), texture, Number(size), Number(radius), Number(speed), colorHex, Number(timeout));
    },

    getComboCount: function(entity, id) {
        return StonesBridge.getComboCount(entity, id);
    },

    hasRune: function(entity, runeId) {
        return StonesBridge.hasRune(entity, runeId);
    },

    spawnParticles: function(player, particle, pos, count, sx, sy, sz, speed) {
        StonesBridge.spawnParticles(player, particle, pos, Number(count), Number(sx), Number(sy), Number(sz), Number(speed));
    },
    playSound: function(player, sound, source, volume, pitch) {
        StonesBridge.playSound(player, sound, source || 'players', Number(volume), Number(pitch));
    },
    spawnProjectile: function(player, config, onTick, onHitEntity, onHitBlock) {
        StonesBridge.spawnProjectile(player, config, onTick, onHitEntity, onHitBlock);
    },

    findBlocks: function(player, rx, ry, rz, los, blockFilter, tagFilter, posOverride) {
        return StonesBridge.findBlocks(player, Number(rx), Number(ry), Number(rz), los, blockFilter, tagFilter, posOverride);
    },

	checkBlock: function(level, pos, blocks, tags) {
        return StonesBridge.checkBlock(level, pos, blocks || [], tags || []);
    },
	checkBlock: function(actor, distance, pos, blocks, tags) {
        return StonesBridge.checkBlock(actor, Number(distance || 0), pos, blocks || [], tags || []);
    },
	hasDamageTag: function(event, tag) {
        try {
            return StonesBridge.hasDamageTag(event, tag);
        } catch (e) {
            return false;
        }
    },
	isDamageType: function(event, typeId) {
		try {
			return StonesBridge.isDamageType(event, typeId);
		} catch (e) {
			return false;
		}
	},
	spawnSprite: function(player, pos, velocity, config) {
        StonesBridge.spawnSprite(player, pos, velocity, config);
    },
	spawnBeam: function(player, start, end, type, texture, coreWidth, coronaWidth, r, g, b, a, scrollSpeed, repeat, helixRadius, helixFreq, helixSpeed, lifetime) {
		if (!player || !start || !end) return;

		var bType = type ? String(type) : 'LASER';
		var tex = texture ? String(texture) : 'minecraft:textures/entity/beacon_beam.png';
		var cWidth = (coreWidth !== undefined && coreWidth !== null) ? Number(coreWidth) : 0.2;
		var corWidth = (coronaWidth !== undefined && coronaWidth !== null) ? Number(coronaWidth) : 0.8;
		var cr = (r !== undefined && r !== null) ? Number(r) : 1.0;
		var cg = (g !== undefined && g !== null) ? Number(g) : 1.0;
		var cb = (b !== undefined && b !== null) ? Number(b) : 1.0;
		var ca = (a !== undefined && a !== null) ? Number(a) : 1.0;
		var spd = (scrollSpeed !== undefined && scrollSpeed !== null) ? Number(scrollSpeed) : 0.2;
		var rep = (repeat !== undefined && repeat !== null) ? Number(repeat) : 1.0;
		var hRad = (helixRadius !== undefined && helixRadius !== null) ? Number(helixRadius) : 0.0;
		var hFreq = (helixFreq !== undefined && helixFreq !== null) ? Number(helixFreq) : 0.0;
		var hSpd = (helixSpeed !== undefined && helixSpeed !== null) ? Number(helixSpeed) : 0.0;
		var lt = (lifetime !== undefined && lifetime !== null) ? Math.round(Number(lifetime)) : 40;

		StonesBridge.spawnBeam(player, start, end, bType, tex, cWidth, corWidth, cr, cg, cb, ca, spd, rep, hRad, hFreq, hSpd, lt);
	},
    readNbt: function(target, path) {
        return StonesBridge.readNbt(target, path);
    },
    invoke: function(target, callStr, args) {
        return StonesBridge.invoke(target, callStr, args || []);
    },
	makeMinion: function(target, player) {
        return StonesBridge.makeMinion(target, player);
    },
    setBlock: function(level, pos, blockId) {
        StonesBridge.setBlock(level, pos, blockId);
    },
	dealDamage: function(directAttacker, indirectAttacker, target, amount, damageTypeId) {
		// Die Java-Methode validiert die Parameter selbst und wirft saubere Exceptions, 
		// falls damageTypeId null, leer oder ungültig ist.
		StonesBridge.dealDamage(
			directAttacker, 
			indirectAttacker, 
			target, 
			Number(amount), 
			damageTypeId
		);
	},
    explode: function(player, pos, radius, fire) {
        StonesBridge.explode(player, pos, Number(radius), fire === true || fire === 'true');
    },

    findEntities: function(player, mode, radius, rx, ry, rz, livingOnly, excludeSelf, los, posOverride) {
        return StonesBridge.findEntities(player, mode, Number(radius), Number(rx), Number(ry), Number(rz), livingOnly, excludeSelf, los, posOverride);
    },

    drawMarker: function(player, pos, mode, size, duration) {
        StonesBridge.drawMarker(player, pos, mode, Number(size), Number(duration));
    },

    removeRandomEnchantment: function(target) {
        return StonesBridge.removeRandomEnchantment(target);
    }
};

// =============================================================================
// ZENTRALE CONTEXT-ERZEUGUNG
// =============================================================================
function buildBaseContext(player, triggerName, target, event) {
    let ctx = {
        player: player,
        level: player.level,
        trigger: triggerName,
        target: target || player,
        event: event
    };

    if (triggerName === 'ON_ACTION_BUTTON') {
        ctx.slot = (typeof event === 'number') ? event : (typeof target === 'number' ? target : 0);
        ctx.target = player;
    } else if (triggerName === 'ON_HURT') {
        ctx.attacker = target;
    } else if (triggerName === 'ON_KILL') {
        ctx.victim = target;
    } else if (triggerName === 'ON_BLOCK_BREAK') {
        ctx.blockPos = (event && event.block) ? event.block.pos : player.blockPosition();
    } else if (triggerName === 'ON_PROJECTILE_HIT') {
        if (event && event.hitPos) ctx.hitPos = event.hitPos;
        if (event && event.projectile) ctx.projectile = event.projectile;
    }

    return ctx;
}

// =============================================================================
// VERBINDUNG ZUM JAVA DISPATCHER
// =============================================================================
try {
    StonesBridge.setTriggerConsumer((player, runeId, triggerName, target, event) => {
        let cleanId = runeId.replace('stones:', '');
        let triggerMap = global.Stones.handlers[triggerName];
        if (!triggerMap) return;

        let handler = triggerMap[cleanId] || triggerMap[runeId];
        if (handler) {
            try {
                let ctx = buildBaseContext(player, triggerName, target, event);
                handler(ctx);
            } catch (err) {
                console.error('[Stones KubeJS] Fehler bei Ausfuehrung von ' + cleanId + ' (' + triggerName + '): ' + err);
            }
        }
    });
    console.info('[Stones KubeJS] Trigger-Pipeline erfolgreich mit Java gekoppelt!');
} catch (e) {
    console.error('[Stones KubeJS] Fehler beim Koppeln des TriggerConsumers: ' + e);
}
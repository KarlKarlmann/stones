package net.stones.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.entity.SlotAccess;
import net.stones.enchantment.RuneEnchantment;
import net.stones.enchantment.AmplifyEnchantment;
import net.stones.enchantment.RuneStat;
import net.stones.logic.RuneCalculator;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.InteractionHand;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.api.distmarker.Dist;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class StoneItem extends Item {

    public enum Type { MINOR, MAJOR, MILESTONE }
    private final Type type;

    public StoneItem(Type type) {
        super(new Item.Properties().stacksTo(1).durability(100).rarity(
            type == Type.MAJOR ? Rarity.RARE : (type == Type.MILESTONE ? Rarity.EPIC : Rarity.COMMON)
        ));
        this.type = type;
    }

    public Type getType() { 
        return type; 
    }

    @Override
    public boolean canBeDepleted() {
        return true;
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return true;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) { return UseAnim.BOW; }

    @Override
    public int getUseDuration(ItemStack stack) { return 40; }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (stack.isEnchanted() || stack.getBaseRepairCost() > 0) {
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }
        return InteractionResultHolder.pass(stack);
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack stack, ItemStack cursorStack, Slot slot, ClickAction action, Player player, SlotAccess access) {
        if (action == ClickAction.SECONDARY && cursorStack.isEmpty()) {
            if (player.level().isClientSide) {
                openRuneInfoScreen(stack);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean overrideStackedOnOther(ItemStack stack, Slot slot, ClickAction action, Player player) {
        if (action == ClickAction.SECONDARY && slot.getItem().isEmpty()) {
            if (player.level().isClientSide) {
                openRuneInfoScreen(stack);
            }
            return true;
        }
        return false;
    }

    private void openRuneInfoScreen(ItemStack stack) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            net.minecraft.client.Minecraft.getInstance().setScreen(new net.stones.client.gui.RuneInfoScreen(stack));
        });
    }

    @Override
    public int getMaxStackSize(ItemStack stack) {
        if(stack.isEnchanted()) stack.setRepairCost(1);
        else stack.setRepairCost(0);
        return (stack.isEnchanted() || stack.getBaseRepairCost() > 0) ? 1 : 64;
    } 

    @Override
    public boolean isRepairable(ItemStack stack) {
        return true;
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int count) {
        if (level.isClientSide && count % 5 == 0) {
            level.addParticle(ParticleTypes.ENCHANT, entity.getX(), entity.getY() + 1.2, entity.getZ(), 0, 0.1, 0);
            if (count % 10 == 0) {
                entity.playSound(SoundEvents.GRINDSTONE_USE, 0.5F, 1.5F);
            }
        }
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player) {
            Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
            
            int totalXp = 0;
            for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
                Enchantment companion = entry.getKey();
                int lvl = entry.getValue();
                if (!companion.isCurse()) {
                    int xpBase = (companion.getMinCost(lvl) + companion.getMaxCost(lvl)) / 4;
                    totalXp += Math.max(1, xpBase / 2);
                }
            }

            if (totalXp > 0) {
                player.giveExperiencePoints(totalXp);
            }
            
            EnchantmentHelper.setEnchantments(new HashMap<>(), stack);
            stack.setRepairCost(0);
            if (stack.hasTag() && stack.getTag().isEmpty()) stack.setTag(null);
            
            level.playSound(null, player.getX(), player.getY(), player.getZ(), 
                SoundEvents.GRINDSTONE_USE, SoundSource.PLAYERS, 1.0f, 1.0f);
            player.displayClientMessage(Component.translatable("tooltip.stones.grind.3", totalXp), true);
        }
        return stack;
    }
	
    @Override
    public boolean isEnchantable(ItemStack pStack) { return true; }

    @Override
    public int getEnchantmentValue() { return 15; }

    @Override
    public boolean canApplyAtEnchantingTable(ItemStack stack, Enchantment enchantment) {
        if (enchantment instanceof AmplifyEnchantment) return true;
        if (enchantment instanceof RuneEnchantment runeEnch) {
            return runeEnch.type.name().equals(this.type.name());
        }
        return enchantment == Enchantments.VANISHING_CURSE || enchantment == Enchantments.BINDING_CURSE;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        addFullRuneTooltip(stack, tooltip, -1);
    }

    public static void addFullRuneTooltip(ItemStack stack, List<Component> tooltip, int socketLevel) {
        if (stack.isEmpty()) return;

        int reqLevel = RuneCalculator.getRequiredLevel(stack);
        boolean hasCurse = EnchantmentHelper.getItemEnchantmentLevel(Enchantments.VANISHING_CURSE, stack) > 0 ||
                           EnchantmentHelper.getItemEnchantmentLevel(Enchantments.BINDING_CURSE, stack) > 0;

        tooltip.add(Component.translatable("tooltip.stones.required_level", reqLevel).withStyle(hasCurse ? ChatFormatting.GREEN : ChatFormatting.BLUE));
        if (hasCurse) tooltip.add(Component.translatable("tooltip.stones.reduced_by_curse").withStyle(ChatFormatting.DARK_GREEN));
        tooltip.add(Component.empty());

        int ampLvl = 0;
        Map<Enchantment, Integer> enchants = EnchantmentHelper.getEnchantments(stack);
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof AmplifyEnchantment) {
                ampLvl = entry.getValue();
                break;
            }
        }
        
        double mult = AmplifyEnchantment.getMultiplier(ampLvl);
        if (ampLvl > 0) {
            renderAmplifyTooltip(tooltip, ampLvl, mult);
        }

        List<Map.Entry<Enchantment, Integer>> runeEntries = new ArrayList<>();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            if (entry.getKey() instanceof RuneEnchantment) {
                runeEntries.add(entry);
            }
        }

        boolean hasUpgrades = ampLvl > 0;
        boolean showMinorMajorStats = enchants.size() < 4;

        if (!runeEntries.isEmpty()) {
            hasUpgrades = true;
            
            if (isClientShiftDown()) {
                int activeIndex = (int) ((System.currentTimeMillis() / 3000) % runeEntries.size());

                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("tooltip.stones.details_carousel", (activeIndex + 1), runeEntries.size())
                        .withStyle(ChatFormatting.DARK_GRAY));
                
                for (int i = 0; i < runeEntries.size(); i++) {
                    Map.Entry<Enchantment, Integer> entry = runeEntries.get(i);
                    RuneEnchantment rune = (RuneEnchantment) entry.getKey();
                    int lvl = entry.getValue();
                    
                    if (i == activeIndex) {
                        tooltip.add(Component.literal(" ▼ ").withStyle(ChatFormatting.AQUA)
                            .append(rune.getFullname(lvl).copy().withStyle(ChatFormatting.GOLD, ChatFormatting.UNDERLINE)));
                        
                        renderRuneDetails(tooltip, rune, lvl, socketLevel, mult);
                    } else {
                        tooltip.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                            .append(rune.getFullname(lvl).copy().withStyle(ChatFormatting.GOLD)));
                        
                        if (showMinorMajorStats && (rune.type == RuneEnchantment.Type.MINOR || rune.type == RuneEnchantment.Type.MAJOR)) {
                            renderRuneStatsOnly(tooltip, rune, lvl, socketLevel, mult);
                        }
                    }
                }
            } else {
                for (Map.Entry<Enchantment, Integer> entry : runeEntries) {
                    RuneEnchantment rune = (RuneEnchantment) entry.getKey();
                    int lvl = entry.getValue();

                    tooltip.add(Component.literal(" • ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(rune.getFullname(lvl).copy().withStyle(ChatFormatting.GOLD)));
                    
                    if (showMinorMajorStats && (rune.type == RuneEnchantment.Type.MINOR || rune.type == RuneEnchantment.Type.MAJOR)) {
                        renderRuneStatsOnly(tooltip, rune, lvl, socketLevel, mult);
                    }
                }
                
                tooltip.add(Component.empty());
                tooltip.add(Component.translatable("tooltip.stones.hold_shift_details").withStyle(ChatFormatting.DARK_GRAY));
            }
            tooltip.add(Component.translatable("tooltip.stones.right_click_gui").withStyle(ChatFormatting.DARK_AQUA));
        }

        if ((hasUpgrades || stack.getBaseRepairCost() > 0) && enchants.size() <= 3) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("tooltip.stones.grind.1"));
            tooltip.add(Component.translatable("tooltip.stones.grind.2").withStyle(ChatFormatting.ITALIC));
        }
    }

    private static void renderAmplifyTooltip(List<Component> tooltip, int level, double multiplier) {
        ChatFormatting rarityColor = ChatFormatting.GRAY;
        if (level >= 90) rarityColor = ChatFormatting.RED;
        else if (level >= 60) rarityColor = ChatFormatting.LIGHT_PURPLE;
        else if (level >= 30) rarityColor = ChatFormatting.GOLD;
        else if (level >= 15) rarityColor = ChatFormatting.BLUE;

        tooltip.add(Component.literal("Amplify ").append(Component.translatable("enchantment.level." + level))
                .withStyle(rarityColor, ChatFormatting.BOLD));

        tooltip.add(Component.translatable("tooltip.stones.amplify.1").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));
        tooltip.add(Component.translatable("tooltip.stones.amplify.2").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.ITALIC));

        double percentBonus = (multiplier - 1.0) * 100;
        tooltip.add(Component.literal(" ➤ ").withStyle(ChatFormatting.DARK_GRAY)
            .append(Component.literal("Potenzial: ").withStyle(ChatFormatting.GRAY))
            .append(Component.literal(String.format("+%.1f%%", percentBonus)).withStyle(rarityColor)));
        
        tooltip.add(Component.empty());
    }

    private static void renderRuneStatsOnly(List<Component> tooltip, RuneEnchantment ench, int runeLvl, int sockLvl, double mult) {
        int pLvl = getClientPlayerLevel();

        boolean isSocketed = sockLvl >= 0;
        int activeSock = isSocketed ? sockLvl : 1;

        for (RuneStat stat : ench.getStats()) {
            float val = RuneCalculator.calculateStatValue(stat, runeLvl, activeSock, pLvl, mult);
            MutableComponent line = Component.literal("    ➤ ").withStyle(ChatFormatting.DARK_GRAY)
                .append(RuneEnchantment.resolveComponent(stat.label()).copy().withStyle(ChatFormatting.GRAY)).append(": ")
                .append(Component.literal(String.format("%.1f", val * stat.displayFactor())))
                .append(RuneEnchantment.resolveComponent(stat.suffix()).copy().withStyle(mult > 1.0 ? ChatFormatting.AQUA : ChatFormatting.AQUA));
            
            if (mult > 1.0) line.append(Component.literal(" ✦").withStyle(ChatFormatting.AQUA));

            if (!isSocketed && stat.perLevel() != 0) {
                float boostedGrowth = (float)(stat.perLevel() * mult);
                String sign = boostedGrowth > 0 ? "+" : "";
                Component source = Component.translatable("scaling.stones." + stat.scaling().toLowerCase());
                line.append(Component.literal(" (").append(sign + String.format("%.1f", boostedGrowth * stat.displayFactor()) + RuneEnchantment.resolveComponent(stat.suffix()).getString() + "/").append(source).append(")").withStyle(ChatFormatting.DARK_AQUA));
            }
            tooltip.add(line);
        }

        if (ench.targetAttribute != null) {
            if (isSocketed) {
                double bonus = RuneCalculator.calculateAttributeBonus(ench, runeLvl, pLvl, sockLvl, mult);
                tooltip.add(formatAttributeLine(ench, bonus, mult > 1.0, true));
            } else {
                if (ench.type == RuneEnchantment.Type.MAJOR) {
                    double scaledFactor = ench.factor * runeLvl * mult; 
                    String valStr = (ench.operation != AttributeModifier.Operation.ADDITION) ? String.format("%.1f%%", scaledFactor * 100) : String.format("%.1f", scaledFactor);                    
                    MutableComponent line = Component.literal("    ➤ ").withStyle(ChatFormatting.DARK_GRAY)
                        .append(Component.translatable("tooltip.stones.scaling_info").withStyle(ChatFormatting.GRAY)).append(": ")
                        .append(Component.literal("+" + valStr).withStyle(mult > 1.0 ? ChatFormatting.AQUA : ChatFormatting.GOLD))
                        .append(" ").append(Component.translatable(ench.targetAttribute.getDescriptionId()).withStyle(ChatFormatting.WHITE))
                        .append(Component.translatable("tooltip.stones.levels_exceed_socket").withStyle(ChatFormatting.DARK_AQUA));
                    if (mult > 1.0) line.append(Component.literal(" ✦").withStyle(ChatFormatting.AQUA));
                    tooltip.add(line);
                } else {
                    double previewBonus = (runeLvl * ench.factor) * mult;
                    tooltip.add(formatAttributeLine(ench, previewBonus, mult > 1.0, false));
                }
            }
        }
    }

    private static void renderRuneDetails(List<Component> tooltip, RuneEnchantment ench, int runeLvl, int sockLvl, double mult) {
        renderRuneStatsOnly(tooltip, ench, runeLvl, sockLvl, mult);

        Component desc = ench.getCustomDescription(runeLvl);
        if (desc != null && !desc.getString().isEmpty()) {
            String descStr = desc.getString();
            StringBuilder line = new StringBuilder();
            String indent = "    "; 
            
            for (String word : descStr.split(" ")) {
                if (line.length() + word.length() > 35) {
                    tooltip.add(Component.literal(indent + line.toString()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
                    line = new StringBuilder();
                }
                if (line.length() > 0) line.append(" ");
                line.append(word);
            }
            if (line.length() > 0) {
                tooltip.add(Component.literal(indent + line.toString()).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
            }
        }
    }

    public static Component formatAttributeLine(RuneEnchantment rune, double value, boolean amplified, boolean active) {
        MutableComponent line = Component.literal("    ➤ ").withStyle(ChatFormatting.DARK_GRAY);
        if (!active) line.append(Component.translatable("tooltip.stones.passive_effect").withStyle(ChatFormatting.GRAY)).append(": ");
        else line.append(Component.translatable("tooltip.stones.active_bonus").withStyle(ChatFormatting.GRAY)).append(": ");

        String sign = value >= 0 ? "+" : "";
        String valStr = (rune.operation != AttributeModifier.Operation.ADDITION) ? String.format("%.1f%%", value * 100) : String.format("%.1f", value);
        line.append(Component.literal(sign + valStr).withStyle(amplified ? ChatFormatting.AQUA : ChatFormatting.GOLD))
            .append(" ").append(Component.translatable(rune.targetAttribute.getDescriptionId()).withStyle(ChatFormatting.WHITE));
        if (amplified) line.append(Component.literal(" ✦").withStyle(ChatFormatting.AQUA));
        return line;
    }

    private static boolean isClientShiftDown() {
        try {
            Boolean isDown = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ClientPlayerHelper::isShiftDown);
            return isDown != null && isDown;
        } catch (Exception e) {
            return false;
        }
    }

    private static int getClientPlayerLevel() {
        try {
            Integer level = DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> ClientPlayerHelper::getLevel);
            return level == null ? 0 : level;
        } catch (Exception e) {
            return 0;
        }
    }

    private static class ClientPlayerHelper {
        public static Integer getLevel() {
            Player player = net.minecraft.client.Minecraft.getInstance().player;
            return player != null ? player.experienceLevel : 0;
        }
        public static Boolean isShiftDown() {
            return net.minecraft.client.gui.screens.Screen.hasShiftDown();
        }
    }
}
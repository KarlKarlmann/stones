package net.stones.client.gui.editor;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Isolierte Serialisierungs- und Parsing-Schicht für das Stones Studio.
 * Konvertiert Datenstrukturen verlustfrei zwischen JSON und den visuellen TreeNodes.
 */
public class StudioSerializer {

    public static String getReadableText(JsonObject json, TreeNode.Type type) {
        if (json == null || !json.has("type")) {
            return Component.translatable("gui.stones.studio.serializer.empty_node").getString();
        }
        String t = json.get("type").getAsString();

        if (type == TreeNode.Type.ACTION) {
            return switch (t) {
                case "stones:invoke" -> Component.translatable("gui.stones.studio.serializer.action.invoke",
                        json.has("call") ? json.get("call").getAsString() : "").getString();
                case "stones:math" -> Component.translatable("gui.stones.studio.serializer.action.math",
                        json.has("operation") ? json.get("operation").getAsString() : "",
                        json.has("value") ? json.get("value").getAsString() : "").getString();
                case "stones:apply_effect" -> Component.translatable("gui.stones.studio.serializer.action.apply_effect",
                        json.has("effect") ? json.get("effect").getAsString() : "").getString();
                case "stones:modify_damage" -> Component.translatable("gui.stones.studio.serializer.action.modify_damage",
                        json.has("add") ? json.get("add").getAsString() : "0").getString();
                case "stones:spawn_particles" -> Component.translatable("gui.stones.studio.serializer.action.spawn_particles",
                        json.has("particle") ? json.get("particle").getAsString() : "").getString();
                case "stones:play_sound" -> Component.translatable("gui.stones.studio.serializer.action.play_sound",
                        json.has("sound") ? json.get("sound").getAsString() : "").getString();
                case "stones:cooldown" -> Component.translatable("gui.stones.studio.serializer.action.cooldown",
                        json.has("ticks") ? json.get("ticks").getAsString() : "").getString();
				case "stones:read_nbt" -> Component.translatable("gui.stones.studio.serializer.action.read_nbt", 
						json.has("path") ? json.get("path").getAsString() : "").getString();
                default -> {
                    String actionKey = "gui.stones.studio.actionselection.action." + t.replace("stones:", "") + ".name";
                    String name = Component.translatable(actionKey).getString();
                    yield name.equals(actionKey) 
                            ? Component.translatable("gui.stones.studio.serializer.action.prefix", t).getString() 
                            : name;
                }
            };
        } else {
            return switch (t) {
                case "stones:has_air" -> Component.translatable("gui.stones.studio.serializer.condition.has_air").getString();
                case "stones:variable_compare" -> Component.translatable("gui.stones.studio.serializer.condition.variable_compare").getString();
                case "stones:chance" -> Component.translatable("gui.stones.studio.serializer.condition.chance").getString();
                default -> {
                    String condKey = "gui.stones.studio.actionselection.condition." + t.replace("stones:", "") + ".name";
                    String name = Component.translatable(condKey).getString();
                    yield name.equals(condKey) 
                            ? Component.translatable("gui.stones.studio.serializer.condition.prefix", t).getString() 
                            : name;
                }
            };
        }
    }

    public static void loadBehaviors(JsonArray behaviors, List<TreeNode> activeTree) {
        for (JsonElement bEl : behaviors) {
            JsonObject bObj = bEl.getAsJsonObject();
            String trigger = bObj.has("trigger") ? bObj.get("trigger").getAsString() : "UNKNOWN";

            TreeNode evtNode = new TreeNode("⚡", Component.translatable("gui.stones.studio.studioserializer.text_01").getString() + trigger, TreeNode.Type.EVENT, null);
            evtNode.rawId = trigger;

            TreeNode condGroup = new TreeNode("🛡️", Component.translatable("gui.stones.studio.studioserializer.text_02").getString(), TreeNode.Type.CATEGORY, evtNode);
            condGroup.rawId = "CONDITIONS";

            String condKey = bObj.has("conditions") ? "conditions" : (bObj.has("condition") ? "condition" : "conditions");
            if (bObj.has(condKey)) {
                JsonElement condElement = bObj.get(condKey);
                if (condElement.isJsonArray()) {
                    for (JsonElement cEl : condElement.getAsJsonArray()) {
                        JsonObject cObj = cEl.getAsJsonObject();
                        TreeNode cNode = new TreeNode("✦", getReadableText(cObj, TreeNode.Type.CONDITION), TreeNode.Type.CONDITION, condGroup);
                        cNode.jsonData = cObj;
                        condGroup.addChild(cNode);
                    }
                } else if (condElement.isJsonObject()) {
                    JsonObject cObj = condElement.getAsJsonObject();
                    TreeNode cNode = new TreeNode("✦", getReadableText(cObj, TreeNode.Type.CONDITION), TreeNode.Type.CONDITION, condGroup);
                    cNode.jsonData = cObj;
                    condGroup.addChild(cNode);
                }
            }
            evtNode.addChild(condGroup);

            TreeNode actGroup = new TreeNode("🎬", Component.translatable("gui.stones.studio.studioserializer.text_03").getString(), TreeNode.Type.CATEGORY, evtNode);
            actGroup.rawId = "ACTIONS";
            if (bObj.has("actions")) {
                for (JsonElement aEl : bObj.getAsJsonArray("actions")) {
                    JsonObject aObj = aEl.getAsJsonObject();
                    TreeNode aNode = new TreeNode("!", getReadableText(aObj, TreeNode.Type.ACTION), TreeNode.Type.ACTION, actGroup);
                    aNode.jsonData = aObj;
                    actGroup.addChild(aNode);
                }
            }
            evtNode.addChild(actGroup);

            activeTree.add(evtNode);
        }
        postProcessTree(activeTree);
    }

    public static JsonArray serializeBehaviors(List<TreeNode> activeTree) {
        JsonArray behaviorsArray = new JsonArray();
        for (TreeNode evtNode : activeTree) {
            if (evtNode.type == TreeNode.Type.EVENT) {
                JsonObject bObj = new JsonObject();
                bObj.addProperty("trigger", evtNode.rawId);

                JsonArray condArray = new JsonArray();
                JsonArray actArray = new JsonArray();

                for (TreeNode catNode : evtNode.children) {
                    if (catNode.rawId.equals("CONDITIONS")) {
                        for (TreeNode cNode : catNode.children) condArray.add(cNode.jsonData.deepCopy());
                    } else if (catNode.rawId.equals("ACTIONS")) {
                        for (TreeNode aNode : catNode.children) actArray.add(aNode.jsonData.deepCopy());
                    }
                }
                if (condArray.size() > 0) bObj.add("conditions", condArray);
                if (actArray.size() > 0) bObj.add("actions", actArray);
                behaviorsArray.add(bObj);
            }
        }
        return behaviorsArray;
    }

    public static void prepareTreeForSaving(List<TreeNode> tree) {
        for (TreeNode node : tree) {
            if (node.type == TreeNode.Type.ACTION && node.jsonData != null && node.jsonData.has("type")) {
                String actType = node.jsonData.get("type").getAsString();
                if (actType.equals("stones:delay")) {
                    for (TreeNode child : node.children) {
                        if (child.type == TreeNode.Type.CATEGORY && child.rawId.equals("ACTIONS")) {
                            JsonArray nestedActions = new JsonArray();
                            prepareTreeForSaving(child.children);
                            for (TreeNode nestedAct : child.children) {
                                nestedActions.add(nestedAct.jsonData.deepCopy());
                            }
                            node.jsonData.add("actions", nestedActions);
                        }
                    }
                } else if (actType.equals("stones:case")) {
                    JsonArray casesArray = new JsonArray();
                    JsonArray defaultArray = new JsonArray();

                    for (TreeNode child : node.children) {
                        if (child.type == TreeNode.Type.CATEGORY && child.rawId.equals("DEFAULT")) {
                            prepareTreeForSaving(child.children);
                            for (TreeNode defaultAct : child.children) {
                                defaultArray.add(defaultAct.jsonData.deepCopy());
                            }
                        } else if (child.type == TreeNode.Type.CATEGORY && child.rawId.equals("CASES")) {
                            for (TreeNode caseNode : child.children) {
                                JsonObject caseObj = new JsonObject();
                                JsonObject caseCond = new JsonObject();
                                JsonArray caseActs = new JsonArray();

                                prepareTreeForSaving(caseNode.children);
                                for (TreeNode caseChild : caseNode.children) {
                                    if (caseChild.type == TreeNode.Type.CATEGORY && (caseChild.rawId.equals("CONDITIONS") || caseChild.rawId.equals("CONDITION"))) {
                                        if (!caseChild.children.isEmpty()) {
                                            caseCond = caseChild.children.get(0).jsonData.deepCopy();
                                        }
                                    } else if (caseChild.type == TreeNode.Type.CATEGORY && caseChild.rawId.equals("ACTIONS")) {
                                        for (TreeNode caseAct : caseChild.children) {
                                            caseActs.add(caseAct.jsonData.deepCopy());
                                        }
                                    }
                                }
                                caseObj.add("conditions", caseCond);
                                caseObj.add("actions", caseActs);
                                casesArray.add(caseObj);
                            }
                        }
                    }
                    node.jsonData.add("cases", casesArray);
                    if (defaultArray.size() > 0) {
                        node.jsonData.add("default", defaultArray);
                    } else {
                        node.jsonData.remove("default");
                    }
                }
            }
            prepareTreeForSaving(node.children);
        }
    }

    public static void postProcessTree(List<TreeNode> tree) {
        for (TreeNode evtNode : tree) {
            postProcessNode(evtNode);
        }
    }

    public static void postProcessNode(TreeNode node) {
        if (node.type == TreeNode.Type.ACTION && node.jsonData != null && node.jsonData.has("type")) {
            String actType = node.jsonData.get("type").getAsString();
            if (actType.equals("stones:delay") && node.children.isEmpty()) {
                TreeNode delayedActionsGroup = new TreeNode("🎬", Component.translatable("gui.stones.studio.studioserializer.text_04").getString(), TreeNode.Type.CATEGORY, node);
                delayedActionsGroup.rawId = "ACTIONS";
                node.addChild(delayedActionsGroup);

                if (node.jsonData.has("actions")) {
                    JsonArray arr = node.jsonData.getAsJsonArray("actions");
                    for (JsonElement e : arr) {
                        JsonObject aObj = e.getAsJsonObject();
                        TreeNode childNode = new TreeNode("!", getReadableText(aObj, TreeNode.Type.ACTION), TreeNode.Type.ACTION, delayedActionsGroup);
                        childNode.jsonData = aObj;
                        delayedActionsGroup.addChild(childNode);
                        postProcessNode(childNode);
                    }
                }
            } else if (actType.equals("stones:case") && node.children.isEmpty()) {
                TreeNode casesGroup = new TreeNode("📁", Component.translatable("gui.stones.studio.studioserializer.text_05").getString(), TreeNode.Type.CATEGORY, node);
                casesGroup.rawId = "CASES";
                node.addChild(casesGroup);

                TreeNode defaultGroup = new TreeNode("🎬", Component.translatable("gui.stones.studio.studioserializer.text_06").getString(), TreeNode.Type.CATEGORY, node);
                defaultGroup.rawId = "DEFAULT";
                node.addChild(defaultGroup);

                if (node.jsonData.has("cases")) {
                    JsonArray arr = node.jsonData.getAsJsonArray("cases");
                    int index = 1;
                    for (JsonElement e : arr) {
                        JsonObject caseObj = e.getAsJsonObject();
                        TreeNode caseNode = new TreeNode("🔍", Component.translatable("gui.stones.studio.studioserializer.text_07").getString() + (index++), TreeNode.Type.CATEGORY, casesGroup);
                        caseNode.rawId = "CASE";
                        casesGroup.addChild(caseNode);

                        TreeNode caseCond = new TreeNode("🛡️", Component.translatable("gui.stones.studio.studioserializer.text_08").getString(), TreeNode.Type.CATEGORY, caseNode);
                        caseCond.rawId = "CONDITIONS";
                        caseNode.addChild(caseCond);

                        String caseCondKey = caseObj.has("conditions") ? "conditions" : (caseObj.has("condition") ? "condition" : "conditions");
                        if (caseObj.has(caseCondKey)) {
                            JsonObject cObj = caseObj.getAsJsonObject(caseCondKey);
                            TreeNode cNode = new TreeNode("✦", getReadableText(cObj, TreeNode.Type.CONDITION), TreeNode.Type.CONDITION, caseCond);
                            cNode.jsonData = cObj;
                            caseCond.addChild(cNode);
                        }

                        TreeNode caseActions = new TreeNode("🎬", Component.translatable("gui.stones.studio.studioserializer.text_09").getString(), TreeNode.Type.CATEGORY, caseNode);
                        caseActions.rawId = "ACTIONS";
                        caseNode.addChild(caseActions);

                        if (caseObj.has("actions")) {
                            JsonArray acts = caseObj.getAsJsonArray("actions");
                            for (JsonElement ae : acts) {
                                JsonObject aObj = ae.getAsJsonObject();
                                TreeNode childNode = new TreeNode("!", getReadableText(aObj, TreeNode.Type.ACTION), TreeNode.Type.ACTION, caseActions);
                                childNode.jsonData = aObj;
                                caseActions.addChild(childNode);
                                postProcessNode(childNode);
                            }
                        }
                    }
                }

                if (node.jsonData.has("default")) {
                    JsonArray arr = node.jsonData.getAsJsonArray("default");
                    for (JsonElement e : arr) {
                        JsonObject aObj = e.getAsJsonObject();
                        TreeNode childNode = new TreeNode("!", getReadableText(aObj, TreeNode.Type.ACTION), TreeNode.Type.ACTION, defaultGroup);
                        childNode.jsonData = aObj;
                        defaultGroup.addChild(childNode);
                        postProcessNode(childNode);
                    }
                }
            }
        }
        for (TreeNode child : new ArrayList<>(node.children)) {
            postProcessNode(child);
        }
    }

    public static JsonArray serializeTreeState(List<TreeNode> tree) {
        JsonArray behaviorsArray = new JsonArray();
        for (TreeNode evtNode : tree) {
            if (evtNode.type == TreeNode.Type.EVENT) {
                JsonObject bObj = new JsonObject();
                bObj.addProperty("trigger", evtNode.rawId);

                JsonArray condArray = new JsonArray();
                JsonArray actArray = new JsonArray();

                for (TreeNode catNode : evtNode.children) {
                    if (catNode.rawId.equals("CONDITIONS")) {
                        for (TreeNode cNode : catNode.children) condArray.add(cNode.jsonData.deepCopy());
                    } else if (catNode.rawId.equals("ACTIONS")) {
                        for (TreeNode aNode : catNode.children) actArray.add(aNode.jsonData.deepCopy());
                    }
                }
                if (condArray.size() > 0) bObj.add("conditions", condArray);
                if (actArray.size() > 0) bObj.add("actions", actArray);
                behaviorsArray.add(bObj);
            }
        }
        return behaviorsArray;
    }

    public static void restoreTreeState(String stateStr, List<TreeNode> targetTree) {
        JsonArray behaviorsArray = JsonParser.parseString(stateStr).getAsJsonArray();
        targetTree.clear();
        for (JsonElement bEl : behaviorsArray) {
            JsonObject bObj = bEl.getAsJsonObject();
            String trigger = bObj.has("trigger") ? bObj.get("trigger").getAsString() : "UNKNOWN";

            TreeNode evtNode = new TreeNode("⚡", Component.translatable("gui.stones.studio.studioserializer.text_10").getString() + trigger, TreeNode.Type.EVENT, null);
            evtNode.rawId = trigger;

            TreeNode condGroup = new TreeNode("🛡️", Component.translatable("gui.stones.studio.studioserializer.text_11").getString(), TreeNode.Type.CATEGORY, evtNode);
            condGroup.rawId = "CONDITIONS";

            String condKey = bObj.has("conditions") ? "conditions" : (bObj.has("condition") ? "condition" : "conditions");
            if (bObj.has(condKey)) {
                JsonElement condElement = bObj.get(condKey);
                if (condElement.isJsonArray()) {
                    for (JsonElement cEl : condElement.getAsJsonArray()) {
                        JsonObject cObj = cEl.getAsJsonObject().deepCopy();
                        TreeNode cNode = new TreeNode("✦", getReadableText(cObj, TreeNode.Type.CONDITION), TreeNode.Type.CONDITION, condGroup);
                        cNode.jsonData = cObj;
                        condGroup.addChild(cNode);
                    }
                } else if (condElement.isJsonObject()) {
                    JsonObject cObj = condElement.getAsJsonObject().deepCopy();
                    TreeNode cNode = new TreeNode("✦", getReadableText(cObj, TreeNode.Type.CONDITION), TreeNode.Type.CONDITION, condGroup);
                    cNode.jsonData = cObj;
                    condGroup.addChild(cNode);
                }
            }
            evtNode.addChild(condGroup);

            TreeNode actGroup = new TreeNode("🎬", Component.translatable("gui.stones.studio.studioserializer.text_12").getString(), TreeNode.Type.CATEGORY, evtNode);
            actGroup.rawId = "ACTIONS";
            if (bObj.has("actions")) {
                for (JsonElement aEl : bObj.getAsJsonArray("actions")) {
                    JsonObject aObj = aEl.getAsJsonObject().deepCopy();
                    TreeNode aNode = new TreeNode("!", getReadableText(aObj, TreeNode.Type.ACTION), TreeNode.Type.ACTION, actGroup);
                    aNode.jsonData = aObj;
                    actGroup.addChild(aNode);
                }
            }
            evtNode.addChild(actGroup);

            targetTree.add(evtNode);
        }
        postProcessTree(targetTree);
    }

    public static TreeNode cloneTreeNode(TreeNode src) {
        return cloneTreeNode(src, null);
    }

    public static TreeNode cloneTreeNode(TreeNode src, TreeNode parent) {
        TreeNode dest = new TreeNode(src.icon, src.readableText, src.type, parent);
        dest.rawId = src.rawId;
        dest.isExpanded = src.isExpanded;
        if (src.jsonData != null) {
            dest.jsonData = src.jsonData.deepCopy();
        }
        for (TreeNode child : src.children) {
            dest.addChild(cloneTreeNode(child, dest));
        }
        return dest;
    }
}
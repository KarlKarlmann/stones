package net.stones.client.gui.editor;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;

public class TreeNode {
    public enum Type { EVENT, CATEGORY, CONDITION, ACTION }

    public String icon;
    public String readableText;
    public Type type;
    public List<TreeNode> children = new ArrayList<>();
    public boolean isExpanded = true;
    public TreeNode parent;
    
    // Data Payload
    public String rawId = "";
    public JsonObject jsonData = new JsonObject();
    
    public int hitboxX, hitboxY, hitboxW;

    public TreeNode(String icon, String readableText, Type type, TreeNode parent) {
        this.icon = icon;
        this.readableText = readableText;
        this.type = type;
        this.parent = parent;
    }

    public void addChild(TreeNode child) {
        this.children.add(child);
        child.parent = this;
    }
}
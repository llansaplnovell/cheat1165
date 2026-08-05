package Magic.utils.pathfind.astar;

import net.minecraft.util.BlockPos;

public class Node {
    private int gCost;
    private int hCost;
    private Node parent;
    private BlockPos position;

    public Node(int gCost, int hCost, BlockPos position, Node parent) {
        this.gCost = gCost;
        this.hCost = hCost;
        this.parent = parent;
        this.position = position;
    }

    public void setGCost(int gCost) {
        this.gCost = gCost;
    }

    public void setHCost(int hCost) {
        this.hCost = hCost;
    }

    public int getGCost() {
        return this.gCost;
    }

    public int getHCost() {
        return this.hCost;
    }

    public int getFCost() {
        return this.gCost + this.hCost;
    }

    public Node getParent() {
        return this.parent;
    }

    public void setParent(Node parent) {
        this.parent = parent;
    }

    public BlockPos getPosition() {
        return this.position;
    }
}

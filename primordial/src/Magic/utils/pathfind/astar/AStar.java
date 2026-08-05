package Magic.utils.pathfind.astar;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.minecraft.block.BlockAir;
import net.minecraft.client.Minecraft;
import net.minecraft.util.BlockPos;

public class AStar {
    private Minecraft mc = Minecraft.getMinecraft();

    public List<BlockPos> findPath(BlockPos start, BlockPos end, int maxSteps) {
        List<Node> open = new ArrayList<Node>();
        List<Node> closed = new ArrayList<Node>();
        Node startNode = new Node(distance(start, start.add(1, 0, 0)), distance(start, end), start, null);
        open.add(startNode);
        int steps = 0;
        while (true) {
            open.sort(Comparator.comparingDouble(Node::getFCost).thenComparingInt(Node::getHCost));
            if (open.isEmpty()) {
                return new ArrayList<BlockPos>();
            }
            Node current = open.get(0);
            open.remove(current);
            closed.add(current);
            if (current.getPosition().equals(end) || steps >= maxSteps) {
                return this.getPath(startNode, current);
            }
            for (Node neighbour : this.getNeighbours(start, end, current)) {
                if (!this.isValid(neighbour.getPosition()) || this.isClosed(closed, neighbour.getPosition())) {
                    continue;
                }
                int gCost = current.getGCost() + distance(current.getPosition(), neighbour.getPosition());
                if (gCost >= neighbour.getGCost() && this.isOpen(open, neighbour.getPosition())) {
                    continue;
                }
                neighbour.setGCost(gCost);
                neighbour.setHCost(distance(neighbour.getPosition(), end));
                neighbour.setParent(current);
                open.add(neighbour);
            }
            ++steps;
        }
    }

    private List<Node> getNeighbours(BlockPos start, BlockPos end, Node node) {
        List<Node> neighbours = new ArrayList<Node>();
        for (int y = -1; y <= 1; ++y) {
            for (int x = -1; x <= 1; ++x) {
                for (int z = -1; z <= 1; ++z) {
                    if (x == 0 && z == 0 && y == 0) {
                        continue;
                    }
                    BlockPos position = node.getPosition().add(x, y, z);
                    boolean blocked = false;
                    if (x != 0 && z != 0) {
                        int offsetX = node.getPosition().x - x;
                        int offsetZ = node.getPosition().z - z;
                        if (!this.isValid(position.add(-offsetX, 0, 0)) || !this.isValid(position.add(0, 0, -offsetZ))) {
                            blocked = true;
                        }
                    }
                    if (blocked) {
                        continue;
                    }
                    neighbours.add(new Node(distance(position, start), distance(position, end), position, node));
                }
            }
        }
        return neighbours;
    }

    private List<BlockPos> getPath(Node startNode, Node endNode) {
        List<BlockPos> path = new ArrayList<BlockPos>();
        for (Node node = endNode; node != startNode; node = node.getParent()) {
            path.add(node.getPosition());
        }
        Collections.reverse(path);
        return path;
    }

    private boolean isClosed(List<Node> closed, BlockPos position) {
        for (Node node : closed) {
            if (node.getPosition().equals(position)) {
                return true;
            }
        }
        return false;
    }

    private boolean isOpen(List<Node> open, BlockPos position) {
        for (Node node : open) {
            if (node.getPosition().equals(position)) {
                return true;
            }
        }
        return false;
    }

    private boolean isValid(BlockPos position) {
        return this.mc.theWorld.getBlockState(position).getBlock() instanceof BlockAir
                && this.mc.theWorld.getBlockState(position.add(0, 1, 0)).getBlock() instanceof BlockAir;
    }

    private static int distance(BlockPos from, BlockPos to) {
        int x = to.x - from.x;
        int y = to.y - from.y;
        int z = to.z - from.z;
        return x * x + y * y + z * z;
    }
}

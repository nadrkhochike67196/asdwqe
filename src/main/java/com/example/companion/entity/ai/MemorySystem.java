package com.example.companion.entity.ai;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MemorySystem {
    private final Map<String, List<BlockPos>> resources = new HashMap<>();
    private final List<String> recentFailures = new ArrayList<>();
    private static final int MAX_PER_RESOURCE = 10;
    private static final int MAX_FAILURES = 5;

    public void remember(String resourceType, BlockPos pos) {
        List<BlockPos> list = resources.computeIfAbsent(resourceType, k -> new ArrayList<>());
        if (list.stream().noneMatch(p -> p.equals(pos))) {
            list.add(pos);
            if (list.size() > MAX_PER_RESOURCE) list.remove(0);
        }
    }

    public List<BlockPos> recall(String resourceType) {
        return resources.getOrDefault(resourceType, List.of());
    }

    public void addFailure(String failure) {
        recentFailures.add(failure);
        if (recentFailures.size() > MAX_FAILURES) recentFailures.remove(0);
    }

    public String toJsonSummary() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<BlockPos>> entry : resources.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(entry.getKey()).append("\":").append(entry.getValue().size());
            first = false;
        }
        if (!recentFailures.isEmpty()) {
            sb.append(",\"failures\":[");
            for (int i = 0; i < recentFailures.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(recentFailures.get(i)).append("\"");
            }
            sb.append("]");
        }
        sb.append("}");
        return sb.toString();
    }

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        for (Map.Entry<String, List<BlockPos>> entry : resources.entrySet()) {
            NbtList list = new NbtList();
            for (BlockPos pos : entry.getValue()) {
                NbtCompound p = new NbtCompound();
                p.putInt("x", pos.getX());
                p.putInt("y", pos.getY());
                p.putInt("z", pos.getZ());
                list.add(p);
            }
            nbt.put(entry.getKey(), list);
        }
        return nbt;
    }

    public void fromNbt(NbtCompound nbt) {
        resources.clear();
        for (String key : nbt.getKeys()) {
            NbtList list = nbt.getList(key, 10); // 10 = NbtCompound type
            List<BlockPos> positions = new ArrayList<>();
            for (int i = 0; i < list.size(); i++) {
                NbtCompound p = list.getCompound(i);
                positions.add(new BlockPos(p.getInt("x"), p.getInt("y"), p.getInt("z")));
            }
            resources.put(key, positions);
        }
    }
}

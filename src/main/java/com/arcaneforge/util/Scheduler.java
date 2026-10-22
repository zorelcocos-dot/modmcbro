package com.arcaneforge.util;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.IntConsumer;

/** Minimal server-thread tick scheduler used to animate multi-tick ability VFX and delayed effects. */
public final class Scheduler {
    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> PENDING = new ArrayList<>();

    private Scheduler() {
    }

    /** Runs {@code action} once after {@code delayTicks} server ticks. */
    public static void schedule(int delayTicks, Runnable action) {
        synchronized (PENDING) {
            PENDING.add(new Task(Math.max(0, delayTicks), 1, 1, step -> action.run()));
        }
    }

    /** Runs {@code step} {@code times} times, {@code interval} ticks apart, starting on the next tick. */
    public static void repeat(int times, int interval, IntConsumer step) {
        synchronized (PENDING) {
            PENDING.add(new Task(0, Math.max(1, times), Math.max(1, interval), step));
        }
    }

    public static void tick() {
        synchronized (PENDING) {
            TASKS.addAll(PENDING);
            PENDING.clear();
        }
        Iterator<Task> it = TASKS.iterator();
        while (it.hasNext()) {
            Task task = it.next();
            if (task.delay > 0) {
                task.delay--;
                continue;
            }
            try {
                task.step.accept(task.index);
            } catch (RuntimeException e) {
                com.arcaneforge.ArcaneForge.LOGGER.error("Arcane Forge scheduled task failed", e);
                it.remove();
                continue;
            }
            task.index++;
            if (task.index >= task.times) {
                it.remove();
            } else {
                task.delay = task.interval - 1;
            }
        }
    }

    public static void clear() {
        synchronized (PENDING) {
            PENDING.clear();
        }
        TASKS.clear();
    }

    private static final class Task {
        private int delay;
        private final int times;
        private final int interval;
        private final IntConsumer step;
        private int index;

        private Task(int delay, int times, int interval, IntConsumer step) {
            this.delay = delay;
            this.times = times;
            this.interval = interval;
            this.step = step;
        }
    }
}

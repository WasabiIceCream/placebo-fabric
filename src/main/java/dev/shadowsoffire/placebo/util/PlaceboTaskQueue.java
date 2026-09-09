package dev.shadowsoffire.placebo.util;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Queue;

import org.apache.commons.lang3.tuple.Pair;

import dev.shadowsoffire.placebo.Placebo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.resources.Identifier;

/**
 * Helper class for scheduling transient tick-based tasks on the server.
 * <p>
 * Do not use for critical functionality, since the queue is abandoned entirely if the game closes or crashes.
 *
 * Ported from NeoForge's {@code ServerTickEvent.Post}/{@code ServerStartedEvent}/{@code ServerStoppedEvent}
 * to Fabric API's {@code ServerTickEvents}/{@code ServerLifecycleEvents} equivalents.
 */
public class PlaceboTaskQueue {

    private static final Queue<Pair<Identifier, Task>> TASKS = new ArrayDeque<>();

    static {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Iterator<Pair<Identifier, Task>> it = TASKS.iterator();
            Pair<Identifier, Task> current = null;
            while (it.hasNext()) {
                current = it.next();
                try {
                    if (current.getRight().execute().isCompleted()) {
                        it.remove();
                    }
                }
                catch (Exception ex) {
                    Placebo.LOGGER.error("An exception occurred while running a ticking task with ID {}. It will be terminated.", current.getLeft());
                    it.remove();
                    ex.printStackTrace();
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> TASKS.clear());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> TASKS.clear());
    }

    /**
     * Submits a new task for immediate execution.
     */
    public static void submitTask(Identifier id, Task task) {
        TASKS.add(Pair.of(id, task));
    }

    /**
     * Submits a new task for delayed execution.
     *
     * @param delay The delay, in ticks, before the task begins executing.
     */
    public static void submitDelayedTask(Identifier id, int delay, Task task) {
        TASKS.add(Pair.of(id, new DelayedTask(delay, task)));
    }

    @FunctionalInterface
    public static interface Task {

        /**
         * Executes the task, returning a status specifying if the task finished or not.
         *
         * @return The completion status, either {@link Status#RUNNING} to continue executing or {@link Status#COMPLETED} to stop.
         */
        Status execute();
    }

    public static enum Status {
        RUNNING,
        COMPLETED;

        public boolean isCompleted() {
            return this == COMPLETED;
        }
    }

    private static class DelayedTask implements Task {

        private int delay;
        private Task task;

        private DelayedTask(int delay, Task task) {
            this.delay = delay;
            this.task = task;
        }

        @Override
        public Status execute() {
            if (delay-- > 0) {
                return Status.RUNNING;
            }
            return this.task.execute();
        }

    }

}

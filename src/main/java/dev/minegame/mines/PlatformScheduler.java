package dev.minegame.mines;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Schedules plugin work on the server scheduler available at runtime.
 *
 * <p>Folia does not support the traditional Bukkit scheduler for plugin work.
 * The Folia schedulers are deliberately accessed through reflection so the
 * same jar remains loadable on Bukkit, Spigot, Paper, and Purpur, whose Server
 * interfaces do not all expose the Folia methods.</p>
 */
public final class PlatformScheduler {
    private PlatformScheduler() {
    }

    public static BukkitTask runTask(Plugin plugin, Runnable task) {
        return runGlobal(plugin, task, 0L, 0L, ScheduleType.ONCE);
    }

    public static BukkitTask runTaskLater(Plugin plugin, Runnable task, long delay) {
        return runGlobal(plugin, task, delay, 0L, ScheduleType.ONCE);
    }

    public static BukkitTask runTaskTimer(Plugin plugin, Runnable task, long delay, long period) {
        return runGlobal(plugin, task, delay, period, ScheduleType.REPEATING);
    }

    public static BukkitTask runTask(Plugin plugin, Location location, Runnable task) {
        return runAtLocation(plugin, location, task, 0L, 0L, ScheduleType.ONCE);
    }

    public static BukkitTask runTaskLater(Plugin plugin, Location location, Runnable task, long delay) {
        return runAtLocation(plugin, location, task, delay, 0L, ScheduleType.ONCE);
    }

    public static BukkitTask runTaskTimer(Plugin plugin, Location location, Runnable task, long delay, long period) {
        return runAtLocation(plugin, location, task, delay, period, ScheduleType.REPEATING);
    }

    public static BukkitTask runTaskLater(Plugin plugin, Entity entity, Runnable task, long delay) {
        if (entity == null) {
            return runTaskLater(plugin, task, delay);
        }
        Object entityScheduler = invokeNoArg(entity, "getScheduler");
        if (entityScheduler == null) {
            return runTaskLater(plugin, entity.getLocation(), task, delay);
        }

        PlatformTask handle = new PlatformTask(plugin);
        Consumer<Object> callback = ignored -> runSafely(handle, task);
        Runnable retired = handle::cancel;
        Object scheduled = invoke(entityScheduler, "runDelayed",
                new Class<?>[]{Plugin.class, Consumer.class, Runnable.class, long.class},
                plugin, callback, retired, delay);
        handle.attach(scheduled);
        return handle;
    }

    private static BukkitTask runGlobal(Plugin plugin, Runnable task, long delay, long period, ScheduleType type) {
        Object globalScheduler = invokeNoArg(plugin.getServer(), "getGlobalRegionScheduler");
        if (globalScheduler != null) {
            PlatformTask handle = new PlatformTask(plugin);
            Consumer<Object> callback = ignored -> runSafely(handle, task);
            Object scheduled;
            if (type == ScheduleType.REPEATING) {
                scheduled = invoke(globalScheduler, "runAtFixedRate",
                        new Class<?>[]{Plugin.class, Consumer.class, long.class, long.class},
                        plugin, callback, Math.max(1L, delay), Math.max(1L, period));
            } else if (delay > 0L) {
                scheduled = invoke(globalScheduler, "runDelayed",
                        new Class<?>[]{Plugin.class, Consumer.class, long.class}, plugin, callback, delay);
            } else {
                scheduled = invoke(globalScheduler, "run",
                        new Class<?>[]{Plugin.class, Consumer.class}, plugin, callback);
            }
            handle.attach(scheduled);
            return handle;
        }

        if (type == ScheduleType.REPEATING) {
            return new PlatformTask(plugin, Bukkit.getScheduler().runTaskTimer(plugin, task, delay, period));
        }
        if (delay > 0L) {
            return new PlatformTask(plugin, Bukkit.getScheduler().runTaskLater(plugin, task, delay));
        }
        return new PlatformTask(plugin, Bukkit.getScheduler().runTask(plugin, task));
    }

    private static BukkitTask runAtLocation(Plugin plugin, Location location, Runnable task,
                                            long delay, long period, ScheduleType type) {
        if (location == null || location.getWorld() == null) {
            return runGlobal(plugin, task, delay, period, type);
        }

        Object regionScheduler = invokeNoArg(plugin.getServer(), "getRegionScheduler");
        if (regionScheduler != null) {
            PlatformTask handle = new PlatformTask(plugin);
            Consumer<Object> callback = ignored -> runSafely(handle, task);
            World world = location.getWorld();
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            Object scheduled;
            if (type == ScheduleType.REPEATING) {
                scheduled = invoke(regionScheduler, "runAtFixedRate",
                        new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class, long.class, long.class},
                        plugin, world, chunkX, chunkZ, callback, Math.max(1L, delay), Math.max(1L, period));
            } else if (delay > 0L) {
                scheduled = invoke(regionScheduler, "runDelayed",
                        new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class, long.class},
                        plugin, world, chunkX, chunkZ, callback, delay);
            } else {
                scheduled = invoke(regionScheduler, "run",
                        new Class<?>[]{Plugin.class, World.class, int.class, int.class, Consumer.class},
                        plugin, world, chunkX, chunkZ, callback);
            }
            handle.attach(scheduled);
            return handle;
        }

        return runGlobal(plugin, task, delay, period, type);
    }

    private static void runSafely(PlatformTask handle, Runnable task) {
        if (!handle.isCancelled()) {
            task.run();
        }
    }

    private static Object invokeNoArg(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            Method method = accessibleMethod(target, methodName, new Class<?>[0]);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... arguments) {
        try {
            Method method = accessibleMethod(target, methodName, parameterTypes);
            return method.invoke(target, arguments);
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            throw new IllegalStateException("Server scheduler does not support " + methodName, ex);
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IllegalStateException("Unable to schedule task with " + methodName, cause);
        }
    }

    private static Method accessibleMethod(Object target, String methodName, Class<?>[] parameterTypes)
            throws NoSuchMethodException {
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        method.trySetAccessible();
        return method;
    }

    private enum ScheduleType {
        ONCE,
        REPEATING
    }

    private static final class PlatformTask implements BukkitTask {
        private final Plugin owner;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private volatile Object delegate;

        private PlatformTask(Plugin owner) {
            this.owner = owner;
        }

        private PlatformTask(Plugin owner, BukkitTask delegate) {
            this.owner = owner;
            this.delegate = delegate;
        }

        private void attach(Object delegate) {
            this.delegate = delegate;
            if (cancelled.get() && delegate != null) {
                cancelDelegate(delegate);
            }
        }

        @Override
        public int getTaskId() {
            return delegate instanceof BukkitTask task ? task.getTaskId() : -1;
        }

        @Override
        public Plugin getOwner() {
            return owner;
        }

        @Override
        public boolean isSync() {
            return true;
        }

        @Override
        public boolean isCancelled() {
            if (cancelled.get()) {
                return true;
            }
            if (delegate instanceof BukkitTask task) {
                return task.isCancelled();
            }
            Object result = invokeNoArg(delegate, "isCancelled");
            return result instanceof Boolean value && value;
        }

        @Override
        public void cancel() {
            if (cancelled.compareAndSet(false, true) && delegate != null) {
                cancelDelegate(delegate);
            }
        }

        private static void cancelDelegate(Object delegate) {
            if (delegate instanceof BukkitTask task) {
                task.cancel();
                return;
            }
            invoke(delegate, "cancel", new Class<?>[0]);
        }
    }
}

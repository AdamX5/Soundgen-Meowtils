package meowtils.extension.soundgen;

import meowtils.extension.soundgen.engine.AudioEngine;
import meowtils.extension.soundgen.engine.SampleBank;
import meowtils.extension.soundgen.engine.SampleSet;
import meowtils.extension.soundgen.engine.Sound;
import meowtils.extension.soundgen.input.ClickChannel;
import meowtils.extension.soundgen.input.KeyChannel;
import meowtils.extension.soundgen.input.Timing;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.C08PacketPlayerBlockPlacement;
import net.minecraft.network.play.client.C0APacketAnimation;
import net.minecraft.network.play.client.C0BPacketEntityAction;
import net.minecraft.util.MovementInput;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;
import wtf.tatp.meowtils.Meowtils;
import wtf.tatp.meowtils.config.Config;
import wtf.tatp.meowtils.event.AttackEntityEvent;
import wtf.tatp.meowtils.event.ClientTickEvent;
import wtf.tatp.meowtils.event.MouseInputEvent;
import wtf.tatp.meowtils.event.PlayerInteractEvent;
import wtf.tatp.meowtils.event.RenderTickEvent;
import wtf.tatp.meowtils.event.SendPacketEvent;
import wtf.tatp.meowtils.event.api.EventTarget;
import wtf.tatp.meowtils.extension.Extension;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Turns what the player's character does into mouse and keyboard sounds.
 *
 * Every action can be read several ways ("read methods"). All methods are observed all the
 * time and counted, so the Debug Readout shows side by side what each one sees; the selected
 * method is the one that makes sound. Switch methods in the GUI while testing to find which
 * matches what you want for each action.
 *
 * Event handlers live in this class because Meowtils' EventManager only scans the
 * registered object's own declared methods.
 */
public class SoundGen extends Extension {

    private static final Minecraft mc = Minecraft.getMinecraft();

    // ------------------------------------------------------------------ read methods
    // Index order in each list is the source id used by the channels.

    static final List<String> RIGHT_METHODS = Arrays.asList("Game Click", "Placement", "Packet", "Keybind", "Raw Mouse");
    static final int R_GAME = 0, R_PLACE = 1, R_PACKET = 2, R_KEYBIND = 3, R_RAW = 4;
    private static final boolean[] RIGHT_TICK_BOUND = {true, true, true, true, false};

    static final List<String> LEFT_METHODS = Arrays.asList("Game Click", "Attack", "Packet", "Keybind", "Raw Mouse");
    static final int L_GAME = 0, L_ATTACK = 1, L_PACKET = 2, L_KEYBIND = 3, L_RAW = 4;
    private static final boolean[] LEFT_TICK_BOUND = {true, true, true, true, false};

    static final List<String> MOVE_METHODS = Arrays.asList("Keybind", "Movement", "Raw Key");
    static final int M_KEYBIND = 0, M_MOVEMENT = 1, M_RAW = 2;

    static final List<String> JUMP_METHODS = Arrays.asList("Keybind", "Movement", "Raw Key", "Physics");
    static final int J_KEYBIND = 0, J_MOVEMENT = 1, J_RAW = 2, J_PHYSICS = 3;

    static final List<String> SNEAK_METHODS = Arrays.asList("Keybind", "Movement", "Player", "Raw Key", "Packet");
    static final int SN_KEYBIND = 0, SN_MOVEMENT = 1, SN_PLAYER = 2, SN_RAW = 3, SN_PACKET = 4;

    static final List<String> SPRINT_METHODS = Arrays.asList("Keybind", "Player", "Raw Key", "Packet");
    static final int SP_KEYBIND = 0, SP_PLAYER = 1, SP_RAW = 2, SP_PACKET = 3;

    static final List<String> CLICK_STYLES = Arrays.asList("Clicks", "Loop");
    static final List<String> RELEASE_MODES = Arrays.asList("Real", "Synthetic", "Off");
    static final List<String> TIMING_MODES = Arrays.asList("Spread", "Instant");
    static final List<String> SELECTION_MODES = Arrays.asList("Shuffle", "Random");

    private static final String[] CLICK_SHORT_R = {"game", "place", "pkt", "key", "raw"};
    private static final String[] CLICK_SHORT_L = {"game", "atk", "pkt", "key", "raw"};
    private static final String[] SNEAK_SHORT = {"key", "move", "player", "raw", "pkt"};
    private static final String[] JUMP_SHORT = {"key", "move", "raw", "phys"};

    // ------------------------------------------------------------------ config

    @Config public boolean enabled = true;
    @Config public int key = 0;

    @Config public int masterVolume = 100;
    @Config public boolean debug = false;

    @Config public String outputDevice = "";
    @Config public int bufferMs = 20;
    @Config public String soundPack = "default";
    @Config public String selection = "Shuffle";
    @Config public boolean autoTrim = true;
    @Config public boolean matchLevels = true;

    @Config public String timingMode = "Spread";
    @Config public int clickJitterMs = 3;
    @Config public int keyJitterMs = 6;
    @Config public int volumeVariation = 10;
    @Config public int pitchVariation = 3;

    @Config public boolean rightEnabled = true;
    @Config public String rightMethod = "Game Click";
    @Config public String rightStyle = "Clicks";
    @Config public int rightVolume = 100;
    @Config public int rightHoldTimeoutMs = 120;
    @Config public boolean rightEndClick = true;

    @Config public boolean leftEnabled = true;
    @Config public String leftMethod = "Game Click";
    @Config public int leftVolume = 100;

    @Config public int keysVolume = 100;
    @Config public int syntheticReleaseMs = 80;
    @Config public int minHoldMs = 30;

    @Config public boolean wEnabled = true;
    @Config public String wMethod = "Keybind";
    @Config public String wRelease = "Real";
    @Config public boolean aEnabled = true;
    @Config public String aMethod = "Keybind";
    @Config public String aRelease = "Real";
    @Config public boolean sEnabled = true;
    @Config public String sMethod = "Keybind";
    @Config public String sRelease = "Real";
    @Config public boolean dEnabled = true;
    @Config public String dMethod = "Keybind";
    @Config public String dRelease = "Real";
    @Config public boolean spaceEnabled = true;
    @Config public String spaceMethod = "Keybind";
    @Config public String spaceRelease = "Real";
    @Config public boolean shiftEnabled = true;
    @Config public String shiftMethod = "Player";
    @Config public String shiftRelease = "Real";
    @Config public boolean sprintEnabled = false;
    @Config public String sprintMethod = "Keybind";
    @Config public String sprintRelease = "Real";

    // ------------------------------------------------------------------ runtime

    private final AudioEngine engine = new AudioEngine();
    private final Timing timing = new Timing();
    private final ClickChannel right = new ClickChannel(engine, timing, Sound.DRAG, Sound.DRAG_LOOP, Sound.DRAG_END, 1, RIGHT_TICK_BOUND);
    private final ClickChannel left = new ClickChannel(engine, timing, Sound.BUTTERFLY, Sound.BUTTERFLY, Sound.BUTTERFLY, 2, LEFT_TICK_BOUND);
    private final KeyChannel w = new KeyChannel(engine, timing, Sound.W_DOWN, Sound.W_UP, MOVE_METHODS.size());
    private final KeyChannel a = new KeyChannel(engine, timing, Sound.A_DOWN, Sound.A_UP, MOVE_METHODS.size());
    private final KeyChannel s = new KeyChannel(engine, timing, Sound.S_DOWN, Sound.S_UP, MOVE_METHODS.size());
    private final KeyChannel d = new KeyChannel(engine, timing, Sound.D_DOWN, Sound.D_UP, MOVE_METHODS.size());
    private final KeyChannel space = new KeyChannel(engine, timing, Sound.SPACE_DOWN, Sound.SPACE_UP, JUMP_METHODS.size());
    private final KeyChannel shift = new KeyChannel(engine, timing, Sound.SHIFT_DOWN, Sound.SHIFT_UP, SNEAK_METHODS.size());
    private final KeyChannel sprint = new KeyChannel(engine, timing, Sound.SPRINT_DOWN, Sound.SPRINT_UP, SPRINT_METHODS.size());
    private final KeyChannel[] keyChannels = {w, a, s, d, space, shift, sprint};

    private final Queue<String> messages = new ConcurrentLinkedQueue<String>();
    private final AtomicBoolean loading = new AtomicBoolean();
    private boolean wasOnGround = true;
    private int ticks;

    public SoundGen() {
        super("SoundGen", "Action-driven click & key ASMR");

        slider("Master Volume", 0, 200, 5, "%", "masterVolume", int.class);
        check("Debug Readout", "debug");

        expand("Output", e -> {
            e.text("Output Device", "outputDevice");
            e.slider("Buffer", 5, 100, 1, "ms", "bufferMs", int.class);
            e.button("Apply Output", 1f, this::restartAudio);
            e.button("List Devices", 1f, this::listDevices);
        });

        expand("Sounds", e -> {
            e.text("Sound Pack", "soundPack");
            e.mode("Variation Order", SELECTION_MODES, "selection");
            e.check("Auto Trim", "autoTrim");
            e.check("Match Levels", "matchLevels");
            e.button("Reload Sounds", 1f, this::reloadSounds);
            e.button("Test Sounds", 1f, this::testSounds);
        });

        expand("Timing", e -> {
            e.mode("Click Timing", TIMING_MODES, "timingMode");
            e.slider("Click Jitter", 0, 20, 1, "ms", "clickJitterMs", int.class);
            e.slider("Key Jitter", 0, 30, 1, "ms", "keyJitterMs", int.class);
            e.slider("Volume Variation", 0, 40, 1, "%", "volumeVariation", int.class);
            e.slider("Pitch Variation", 0, 10, 1, "%", "pitchVariation", int.class);
        });

        expand("Right Click (Drag)", e -> {
            e.check("Right Sound", "rightEnabled");
            e.mode("Right Read", RIGHT_METHODS, "rightMethod");
            e.mode("Right Style", CLICK_STYLES, "rightStyle");
            e.slider("Right Volume", 0, 200, 5, "%", "rightVolume", int.class);
            e.slider("Loop Hold Timeout", 60, 300, 5, "ms", "rightHoldTimeoutMs", int.class);
            e.check("Loop End Click", "rightEndClick");
        });

        expand("Left Click (Butterfly)", e -> {
            e.check("Left Sound", "leftEnabled");
            e.mode("Left Read", LEFT_METHODS, "leftMethod");
            e.slider("Left Volume", 0, 200, 5, "%", "leftVolume", int.class);
        });

        expand("Keys", e -> {
            e.slider("Keys Volume", 0, 200, 5, "%", "keysVolume", int.class);
            e.slider("Synthetic Release", 20, 250, 5, "ms", "syntheticReleaseMs", int.class);
            e.slider("Min Hold", 0, 80, 1, "ms", "minHoldMs", int.class);
        });
        keyExpand("W", "w", MOVE_METHODS);
        keyExpand("A", "a", MOVE_METHODS);
        keyExpand("S", "s", MOVE_METHODS);
        keyExpand("D", "d", MOVE_METHODS);
        keyExpand("Space", "space", JUMP_METHODS);
        keyExpand("Shift", "shift", SNEAK_METHODS);
        keyExpand("Sprint", "sprint", SPRINT_METHODS);
    }

    private void keyExpand(String label, String prefix, List<String> methods) {
        expand(label, e -> {
            e.check(label + " Sound", prefix + "Enabled");
            e.mode(label + " Read", methods, prefix + "Method");
            e.mode(label + " Release", RELEASE_MODES, prefix + "Release");
        });
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    public void onEnable() {
        applySettings();
        resetChannels();
        say(engine.start(outputDevice, bufferMs));
        reloadSounds();
    }

    @Override
    public void onDisable() {
        engine.stop();
        resetChannels();
    }

    private void resetChannels() {
        right.reset();
        left.reset();
        for (KeyChannel k : keyChannels) k.reset();
        wasOnGround = true;
    }

    /** {@code <game dir>/meowtils/soundgen/packs/<pack>}. The pack name is sanitised so it can't escape that folder. */
    private File soundDir() {
        String pack = soundPack == null ? "" : soundPack.trim().replaceAll("[^A-Za-z0-9 _.-]", "").replace("..", "");
        if (pack.isEmpty()) pack = "default";
        return new File(new File(new File(Meowtils.MEOWTILS_DIR, "soundgen"), "packs"), pack);
    }

    private void restartAudio() {
        applySettings();
        resetChannels();
        say(engine.start(outputDevice, bufferMs));
    }

    private void listDevices() {
        List<String> devices = AudioEngine.listDevices();
        say("output devices (type part of a name into Output Device, then Apply Output):");
        for (String name : devices) say(" - " + name);
        if (devices.isEmpty()) say(" (none found)");
    }

    /** Decodes on a background thread; the engine swaps to the new samples atomically. */
    private void reloadSounds() {
        if (!loading.compareAndSet(false, true)) return;
        final File dir = soundDir();
        final boolean trim = autoTrim;
        final boolean match = matchLevels;
        Thread t = new Thread(() -> {
            try {
                SampleBank.Result result = SampleBank.load(dir, trim, match);
                engine.setSamples(result.sets);
                messages.add("pack '" + dir.getName() + "': " + result.recorded + " recordings loaded, "
                        + result.placeholders + " slots on placeholders");
                for (String warning : result.warnings) messages.add("§c" + warning);
            } catch (RuntimeException e) {
                messages.add("§csound loading failed: " + e);
            } finally {
                loading.set(false);
            }
        }, "SoundGen-Loader");
        t.setDaemon(true);
        t.start();
    }

    private void testSounds() {
        long now = System.nanoTime();
        for (int i = 0; i < 6; i++) engine.play(Sound.DRAG, rightVolume / 100f, now + i * 45_000_000L);
        for (int i = 0; i < 4; i++) engine.play(Sound.BUTTERFLY, leftVolume / 100f, now + 350_000_000L + i * 80_000_000L);
        engine.play(Sound.SPACE_DOWN, keysVolume / 100f, now + 750_000_000L);
        engine.play(Sound.SPACE_UP, keysVolume / 100f, now + 850_000_000L);
    }

    private void say(String message) {
        messages.add(message);
    }

    // ------------------------------------------------------------------ settings

    /** Pushes GUI values into the channels. Cheap enough to run every tick. */
    private void applySettings() {
        engine.setMasterGain(masterVolume / 100f);
        engine.setGainJitter(volumeVariation / 100f);
        engine.setPitchJitter(pitchVariation / 100f);
        engine.setSelection(index(SELECTION_MODES, selection) == 1 ? SampleSet.RANDOM : SampleSet.SHUFFLE);

        timing.spread = !"Instant".equals(timingMode);
        timing.clickJitterMs = clickJitterMs;
        timing.keyJitterMs = keyJitterMs;

        right.enabled = rightEnabled;
        right.method = index(RIGHT_METHODS, rightMethod);
        right.style = index(CLICK_STYLES, rightStyle);
        right.gain = rightVolume / 100f;
        right.holdTimeoutMs = rightHoldTimeoutMs;
        right.endClick = rightEndClick;

        left.enabled = leftEnabled;
        left.method = index(LEFT_METHODS, leftMethod);
        left.style = ClickChannel.STYLE_CLICKS;
        left.gain = leftVolume / 100f;

        applyKey(w, wEnabled, MOVE_METHODS, wMethod, wRelease);
        applyKey(a, aEnabled, MOVE_METHODS, aMethod, aRelease);
        applyKey(s, sEnabled, MOVE_METHODS, sMethod, sRelease);
        applyKey(d, dEnabled, MOVE_METHODS, dMethod, dRelease);
        applyKey(space, spaceEnabled, JUMP_METHODS, spaceMethod, spaceRelease);
        applyKey(shift, shiftEnabled, SNEAK_METHODS, shiftMethod, shiftRelease);
        applyKey(sprint, sprintEnabled, SPRINT_METHODS, sprintMethod, sprintRelease);
    }

    private void applyKey(KeyChannel k, boolean on, List<String> methods, String method, String release) {
        k.enabled = on;
        k.method = index(methods, method);
        k.releaseMode = index(RELEASE_MODES, release);
        k.gain = keysVolume / 100f;
        k.syntheticReleaseMs = syntheticReleaseMs;
        k.minHoldMs = minHoldMs;
    }

    private static int index(List<String> options, String value) {
        int i = options.indexOf(value);
        return i < 0 ? 0 : i;
    }

    // ------------------------------------------------------------------ events

    @EventTarget
    public void onMouse(MouseInputEvent event) {
        if (!enabled) return;
        long now = System.nanoTime();
        if (event.getAction() == MouseInputEvent.Action.RIGHT_CLICK) {
            right.click(R_GAME, now);
        } else if (event.getAction() == MouseInputEvent.Action.LEFT_CLICK) {
            left.click(L_GAME, now);
        }
    }

    @EventTarget
    public void onInteract(PlayerInteractEvent event) {
        if (!enabled || event.getAction() != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) return;
        right.click(R_PLACE, System.nanoTime());
    }

    @EventTarget
    public void onAttack(AttackEntityEvent event) {
        if (!enabled || event.getPlayer() != mc.thePlayer) return;
        left.click(L_ATTACK, System.nanoTime());
    }

    @EventTarget
    public void onSend(SendPacketEvent event) {
        if (!enabled) return;
        Packet<?> packet = event.getPacket();
        long now = System.nanoTime();
        if (packet instanceof C08PacketPlayerBlockPlacement) {
            right.click(R_PACKET, now);
        } else if (packet instanceof C0APacketAnimation) {
            left.click(L_PACKET, now);
        } else if (packet instanceof C0BPacketEntityAction) {
            C0BPacketEntityAction.Action action = ((C0BPacketEntityAction) packet).getAction();
            if (action == C0BPacketEntityAction.Action.START_SNEAKING) {
                shift.state(SN_PACKET, true, now);
            } else if (action == C0BPacketEntityAction.Action.STOP_SNEAKING) {
                shift.state(SN_PACKET, false, now);
            } else if (action == C0BPacketEntityAction.Action.START_SPRINTING) {
                sprint.state(SP_PACKET, true, now);
            } else if (action == C0BPacketEntityAction.Action.STOP_SPRINTING) {
                sprint.state(SP_PACKET, false, now);
            }
        }
    }

    /** Raw input is polled once per rendered frame, so it resolves faster than the 50 ms tick. */
    @EventTarget
    public void onRender(RenderTickEvent event) {
        if (!enabled || event.getPhase() != RenderTickEvent.Phase.PRE || mc.thePlayer == null) return;
        long now = System.nanoTime();
        boolean focus = mc.inGameHasFocus;
        right.held(R_RAW, focus && raw(mc.gameSettings.keyBindUseItem), now);
        left.held(L_RAW, focus && raw(mc.gameSettings.keyBindAttack), now);
        w.state(M_RAW, focus && raw(mc.gameSettings.keyBindForward), now);
        a.state(M_RAW, focus && raw(mc.gameSettings.keyBindLeft), now);
        s.state(M_RAW, focus && raw(mc.gameSettings.keyBindBack), now);
        d.state(M_RAW, focus && raw(mc.gameSettings.keyBindRight), now);
        space.state(J_RAW, focus && raw(mc.gameSettings.keyBindJump), now);
        shift.state(SN_RAW, focus && raw(mc.gameSettings.keyBindSneak), now);
        sprint.state(SP_RAW, focus && raw(mc.gameSettings.keyBindSprint), now);
    }

    @EventTarget
    public void onTick(ClientTickEvent event) {
        if (event.getPhase() != ClientTickEvent.Phase.POST) return;
        flushMessages();
        if (!enabled) return;
        applySettings();

        EntityPlayerSP player = mc.thePlayer;
        long now = System.nanoTime();
        if (player != null && mc.theWorld != null) {
            pollTickSources(player, now);
        }
        right.tick(now);
        left.tick(now);

        if (++ticks % 20 == 0) debugReadout();
    }

    private void pollTickSources(EntityPlayerSP player, long now) {
        right.held(R_KEYBIND, mc.gameSettings.keyBindUseItem.isKeyDown(), now);
        left.held(L_KEYBIND, mc.gameSettings.keyBindAttack.isKeyDown(), now);

        w.state(M_KEYBIND, mc.gameSettings.keyBindForward.isKeyDown(), now);
        a.state(M_KEYBIND, mc.gameSettings.keyBindLeft.isKeyDown(), now);
        s.state(M_KEYBIND, mc.gameSettings.keyBindBack.isKeyDown(), now);
        d.state(M_KEYBIND, mc.gameSettings.keyBindRight.isKeyDown(), now);
        space.state(J_KEYBIND, mc.gameSettings.keyBindJump.isKeyDown(), now);
        shift.state(SN_KEYBIND, mc.gameSettings.keyBindSneak.isKeyDown(), now);
        sprint.state(SP_KEYBIND, mc.gameSettings.keyBindSprint.isKeyDown(), now);

        // What the movement code actually used this tick (includes module-driven input).
        MovementInput input = player.movementInput;
        if (input != null) {
            w.state(M_MOVEMENT, input.moveForward > 0, now);
            s.state(M_MOVEMENT, input.moveForward < 0, now);
            a.state(M_MOVEMENT, input.moveStrafe > 0, now);
            d.state(M_MOVEMENT, input.moveStrafe < 0, now);
            space.state(J_MOVEMENT, input.jump, now);
            shift.state(SN_MOVEMENT, input.sneak, now);
        }

        // What the character visibly does.
        shift.state(SN_PLAYER, player.isSneaking(), now);
        sprint.state(SP_PLAYER, player.isSprinting(), now);

        // Jump physics: left the ground this tick with upward speed, and not from a hit.
        boolean onGround = player.onGround;
        boolean knocked = player.hurtTime > 0 && player.hurtTime >= player.maxHurtTime - 2;
        if (wasOnGround && !onGround && player.motionY > 0.2 && !knocked) {
            space.pulse(J_PHYSICS, now);
        }
        wasOnGround = onGround;
    }

    /** Raw physical state, bypassing KeyBinding (so modules pressing keys don't show up). */
    private static boolean raw(KeyBinding binding) {
        int code = binding.getKeyCode();
        if (code < 0) return Mouse.isButtonDown(code + 100); // mouse buttons are stored as button - 100
        return code > 0 && Keyboard.isKeyDown(code);
    }

    // ------------------------------------------------------------------ debug

    private void flushMessages() {
        if (mc.thePlayer == null) return;
        String m;
        while ((m = messages.poll()) != null) Meowtils.addMessage("§8[§dSoundGen§8] §7" + m);
    }

    /** Once per second: how many clicks/presses each read method saw. Counters always reset. */
    private void debugReadout() {
        if (!debug || mc.thePlayer == null) {
            right.clearCounts();
            left.clearCounts();
            for (KeyChannel k : keyChannels) k.clearCounts();
            return;
        }
        String r = counts(right, CLICK_SHORT_R);
        String l = counts(left, CLICK_SHORT_L);
        String sh = counts(shift, SNEAK_SHORT);
        String sp = counts(space, JUMP_SHORT);
        for (KeyChannel k : keyChannels) k.clearCounts();
        if (r != null || l != null) {
            Meowtils.addMessage("§8[§dSG§8] §7R/s " + (r == null ? "-" : r) + " §8| §7L/s " + (l == null ? "-" : l));
        }
        if (sh != null || sp != null) {
            Meowtils.addMessage("§8[§dSG§8] §7Shift " + (sh == null ? "-" : sh) + " §8| §7Space " + (sp == null ? "-" : sp));
        }
        if (engine.consumeClipped()) Meowtils.addMessage("§8[§dSG§8] §coutput clipped, lower the volume");
    }

    /** "game 20 place 20 ..." with the selected method highlighted, or null if all zero. */
    private static String counts(ClickChannel channel, String[] names) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < names.length; i++) {
            int n = channel.takeCount(i);
            any |= n > 0;
            sb.append(i == channel.method ? "§f§n" : "§f").append(names[i]).append("§r§f ").append(n).append(' ');
        }
        return any ? sb.toString().trim() : null;
    }

    private static String counts(KeyChannel channel, String[] names) {
        StringBuilder sb = new StringBuilder();
        boolean any = false;
        for (int i = 0; i < names.length; i++) {
            int n = channel.takeCount(i);
            any |= n > 0;
            sb.append(i == channel.method ? "§f§n" : "§f").append(names[i]).append("§r§f ").append(n).append(' ');
        }
        return any ? sb.toString().trim() : null;
    }
}

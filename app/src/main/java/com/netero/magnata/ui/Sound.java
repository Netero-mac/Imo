package com.netero.magnata.ui;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.util.HashMap;

/** Efeitos sonoros curtos (arquivos em res/raw). */
public class Sound {
    private static final String[] NAMES = {
            "dice", "step", "land", "jump", "coin", "buy", "bad", "turn", "card", "tick", "spin", "win", "pop"
    };

    private final SoundPool pool;
    private final HashMap<String, Integer> ids = new HashMap<>();
    public boolean enabled = true;

    public Sound(Context ctx) {
        AudioAttributes attrs = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build();
        pool = new SoundPool.Builder().setMaxStreams(6).setAudioAttributes(attrs).build();
        for (String n : NAMES) {
            int res = ctx.getResources().getIdentifier(n, "raw", ctx.getPackageName());
            if (res != 0) ids.put(n, pool.load(ctx, res, 1));
        }
    }

    public void play(String name) {
        if (!enabled) return;
        Integer id = ids.get(name);
        if (id == null) return;
        float vol = name.equals("tick") || name.equals("step") ? 0.5f : 0.9f;
        pool.play(id, vol, vol, 1, 0, 1f);
    }

    public void release() { pool.release(); }
}

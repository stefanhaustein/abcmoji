package org.kobjects.abcnotation;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;

import java.io.IOException;
import java.util.HashSet;

public class SampleManager {
    private final Context context;
    private final HashSet<String> sounds = new HashSet<>();

    public SampleManager(Context context) {
        this.context = context;
        try {
            for (String s : context.getAssets().list("sound")) {
                sounds.add(s);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getFileName(String name) {
        if (sounds.contains(name + ".mp3")) {
            return "sound/" + name + ".mp3";
        }
        if (sounds.contains(name + ".wav")) {
            return "sound/" + name + ".wav";
        }
        return null;
    }

    public int getDurationMs(String name) {
        String fileName = getFileName(name);
        if (fileName == null) {
            return 0;
        }
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try (final AssetFileDescriptor fd = context.getAssets().openFd(fileName)) {
            mmr.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
            String durationStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return Integer.parseInt(durationStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    public boolean play(String name, final Runnable callback) {
        final String fileName = getFileName(name);
        if (fileName == null) {
            if (callback != null) {
                callback.run();
            }
            return false;
        }
        try (AssetFileDescriptor descriptor = context.getAssets().openFd(fileName)) {
            final MediaPlayer m = new MediaPlayer();
            m.setDataSource(descriptor.getFileDescriptor(), descriptor.getStartOffset(), descriptor.getLength());
            descriptor.close();
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mediaPlayer) {
                    m.release();
                    if (callback != null) {
                        callback.run();
                    }
                }
            });

            m.prepare();
            m.setVolume(1f, 1f);
            m.setLooping(false);
            m.start();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            callback.run();
            return false;
        }
    }

}

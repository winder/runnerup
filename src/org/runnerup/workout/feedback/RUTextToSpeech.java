/*
 * Copyright (C) 2013 jonas.oreland@gmail.com
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.runnerup.workout.feedback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import android.annotation.TargetApi;
import android.content.Context;
import android.media.AudioManager;
import android.os.Build;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

@TargetApi(Build.VERSION_CODES.FROYO)
public class RUTextToSpeech {

	private static final String UTTERANCE_ID = "RUTextTospeech";
	boolean mute = false;
	boolean trace = true;
	final TextToSpeech textToSpeech;
	final AudioManager audioManager;

	class Entry {
		final String text;
		final HashMap<String, String> params;
		public Entry(String text, HashMap<String, String> params) {
			this.text = text;
			this.params = params;
		}
	};
	HashSet<String> cueSet = new HashSet<String>();
	ArrayList<Entry> cueList = new ArrayList<Entry>();
	
	public RUTextToSpeech(TextToSpeech tts, String mute, Context context) {
		this.textToSpeech = tts;
		this.audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
		this.mute = "yes".equalsIgnoreCase(mute);

		if (this.mute) {
			UtteranceCompletion.setUtteranceCompletedListener(tts, this);
		}
	}
	
	int speak(String text, int queueMode, HashMap<String, String> params) {

		if (queueMode == TextToSpeech.QUEUE_FLUSH) {
			if (trace) {
				System.err.println("speak (mute: " + mute + "): " + text);
			}
			// speak directly
			if (mute) {
				return speakWithMute(text, queueMode, params);
			} else {
				return textToSpeech.speak(text, queueMode, params);
			}
		} else {
			if (!cueSet.contains(text)) {
				if (trace) {
					System.err.println("buffer speak: " + text);
				}
				cueSet.add(text);
				cueList.add(new Entry(text, params));
			} else {
				if (trace) {
					System.err.println("skip buffer (duplicate) speak: " + text);
				}
			}
			return 0;
		}
	}

	/**
	 * Requests audio focus before speaking, if no focus is given nothing is
	 * said.
	 * 
	 * @param text
	 * @param queueMode
	 * @param params
	 * @return
	 */
	private int speakWithMute(String text, int queueMode,
			HashMap<String, String> params) {
		if (requestFocus()) {
			final String utId = UTTERANCE_ID + text.hashCode();
			outstanding.add(utId);
			
			if (params == null) {
				params = new HashMap<String, String>();
			}
			params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utId);
			int res = textToSpeech.speak(text, queueMode, params);
			if (res == TextToSpeech.ERROR) {
				outstanding.remove(utId);
			}
			if (outstanding.isEmpty()) {
				audioManager.abandonAudioFocus(null);
			}
			return res;
		}
		System.err.println("Could not get audio focus.");
		return TextToSpeech.ERROR;
	}

	HashSet<String> outstanding = new HashSet<String>();
	void utteranceCompleted(String id) {
		outstanding.remove(id);
		if (outstanding.isEmpty()) {
			System.err.println("utteranceCompleted: outstanding.isEmpty())");
			audioManager.abandonAudioFocus(null);
		}
	}

	private boolean requestFocus() {
		final AudioManager am = audioManager;
		int result = am.requestAudioFocus(
			null,// afChangeListener,
			AudioManager.STREAM_MUSIC,
			AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
		return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
	}
	
	public void emit() {
		if (cueSet.isEmpty()) {
			return;
		}
		if (mute && requestFocus() == true) {
			for (Entry e : cueList) {
				final String utId = UTTERANCE_ID + e.text.hashCode();
				outstanding.add(utId);
				System.err.println("speak buffer, muted: " + e.text);
			
				HashMap<String, String> params = e.params;
				if (params == null) {
					params = new HashMap<String, String>();
				}
				params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utId);
				int res = textToSpeech.speak(e.text, TextToSpeech.QUEUE_ADD, params);
				if (res == TextToSpeech.ERROR) {
					outstanding.remove(utId);
				}
			}
			if (outstanding.isEmpty()) {
				audioManager.abandonAudioFocus(null);
			}
		} else {
			for (Entry e : cueList) {
				System.err.println("speak buffer: " + e.text);
				textToSpeech.speak(e.text, TextToSpeech.QUEUE_ADD, e.params);
			}
		}
		cueSet.clear();
		cueList.clear();
		System.err.println("outstanding.size(): " + outstanding.size());
	}
}

// separate class to handle FROYO/deprecation
class UtteranceCompletion {

	@SuppressWarnings("deprecation")
	public static void setUtteranceCompletedListener(
			TextToSpeech tts, final RUTextToSpeech ruTextToSpeech) {
		if (Build.VERSION.SDK_INT < 15) {
			tts
			.setOnUtteranceCompletedListener(new android.speech.tts.TextToSpeech.OnUtteranceCompletedListener() {
				@Override
				public void onUtteranceCompleted(String utteranceId) {
					ruTextToSpeech.utteranceCompleted(utteranceId);
				}
			});
		} else {
			tts.setOnUtteranceProgressListener(new UtteranceProgressListener(){
				@Override
				public void onDone(String utteranceId) {
					ruTextToSpeech.utteranceCompleted(utteranceId);
				}
				@Override
				public void onError(String utteranceId) {
					ruTextToSpeech.utteranceCompleted(utteranceId);
				}
				@Override
				public void onStart(String utteranceId) {
				}});
		}
	}
}

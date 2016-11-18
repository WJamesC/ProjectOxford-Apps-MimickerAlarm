/*
 *
 * Copyright (c) Microsoft. All rights reserved.
 * Licensed under the MIT license.
 *
 * Project Oxford: http://ProjectOxford.ai
 *
 * Project Oxford Mimicker Alarm Github:
 * https://github.com/Microsoft/ProjectOxford-Apps-MimickerAlarm
 *
 * Copyright (c) Microsoft Corporation
 * All rights reserved.
 *
 * MIT License:
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED ""AS IS"", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.microsoft.mimickeralarm.mimics;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.hardware.Camera;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.microsoft.mimickeralarm.R;
import com.microsoft.mimickeralarm.utilities.Loggable;
import com.microsoft.mimickeralarm.utilities.Logger;
import com.microsoft.mimickeralarm.utilities.KeyUtilities;
import com.microsoft.projectoxford.vision.VisionServiceRestClient;
import com.microsoft.projectoxford.vision.contract.AnalysisResult;
import com.microsoft.projectoxford.vision.contract.Tag;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Random;

/**
 * The logic and UI implementation of the Color capture mimic game
 *
 * See MimicWithCameraFragment for details of the camera interacts with the UI
 *
 * In vision_question.xml we defined a bunch of color and their acceptable HSL value ranges
 * see https://en.wikipedia.org/wiki/HSL_and_HSV for HSL color space
 *
 * On creation a random color is selected.
 * when an image is capture it is sent to the ProjectOxford Vision API which returns the main colors
 * and accent colors. Accent colors are defined as HEX codes of RGB values which we turn to HSL and
 * compare to see if it's in range of the color we specified.
 *
 */
public class MimicObjectFinderFragment extends MimicWithCameraFragment {
    private VisionServiceRestClient mVisionServiceRestClient;
    private String mQuestionObjectName;

    public final String TAG = this.getClass().getSimpleName();

    @SuppressWarnings("deprecation")
    public MimicObjectFinderFragment() {
        CameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);

        Resources resources = getResources();

        String subscriptionKey = KeyUtilities.getToken(getActivity(), "vision");
        mVisionServiceRestClient = new VisionServiceRestClient(subscriptionKey);

        String[] questions = resources.getStringArray(R.array.vision_object_questions);
        TextView instruction = (TextView) view.findViewById(R.id.instruction_text);
        mQuestionObjectName = questions[new Random().nextInt(questions.length)];
        instruction.setText(String.format(resources.getString(R.string.mimic_vision_object_prompt), mQuestionObjectName));

        Logger.init(getActivity());
        Loggable playGameEvent = new Loggable.UserAction(Loggable.Key.ACTION_GAME_COLOR);
        Logger.track(playGameEvent);

        return view;
    }

    @Override
    public GameResult verify(Bitmap bitmap) {
        GameResult gameResult = new GameResult();
        gameResult.question = ((TextView) getView().findViewById(R.id.instruction_text)).getText().toString();

        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
            ByteArrayInputStream inputStream = new ByteArrayInputStream(output.toByteArray());
            String[] features = {"Tags"};
            String[] details = {};

            AnalysisResult result = mVisionServiceRestClient.analyzeImage(inputStream, features, details);

            Log.d(TAG, "Question object: " + mQuestionObjectName);
            Log.d(TAG, "Actual objects:");
            for (Tag tag: result.tags)
            {
                Log.d(TAG, "name; " + tag.name + " confidence " + tag.confidence);

            }

            for (Tag tag: result.tags)
            {
                if (tag.name.equalsIgnoreCase(mQuestionObjectName))
                {
                    gameResult.success = true;
                    break;
                }
            }

        } catch (Exception ex) {
            Logger.trackException(ex);
        }

        return gameResult;
    }

    @Override
    protected void gameFailure(GameResult gameResult, boolean allowRetry) {
        if (!allowRetry) {
            Loggable.UserAction userAction = new Loggable.UserAction(Loggable.Key.ACTION_GAME_COLOR_TIMEOUT);
            userAction.putProp(Loggable.Key.PROP_QUESTION, mQuestionObjectName);
            Logger.track(userAction);
        }
        super.gameFailure(gameResult, allowRetry);
    }
}
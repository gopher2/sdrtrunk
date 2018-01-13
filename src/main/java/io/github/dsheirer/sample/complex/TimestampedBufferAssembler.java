/*******************************************************************************
 * sdr-trunk
 * Copyright (C) 2014-2018 Dennis Sheirer
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU General Public
 * License as published by  the Free Software Foundation, either version 3 of the License, or  (at your option) any
 * later version.
 *
 * This program is distributed in the hope that it will be useful,  but WITHOUT ANY WARRANTY; without even the implied
 * warranty of  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License  along with this program.
 * If not, see <http://www.gnu.org/licenses/>
 *
 ******************************************************************************/
package io.github.dsheirer.sample.complex;

import io.github.dsheirer.dsp.filter.channelizer.SampleTimestampManager;
import io.github.dsheirer.sample.Listener;
import org.apache.commons.lang3.Validate;

import java.nio.FloatBuffer;

/**
 * Assembles complex float samples into a ComplexBuffer containing an array of floats.  Monitors incoming sample count
 * to accurately assign a relative timestamp to each assembled buffer.
 */
public class TimestampedBufferAssembler
{
    private SampleTimestampManager mTimestampManager;
    private FloatBuffer mBuffer;
    private long mCurrentBufferTimestamp;
    private Listener<ComplexBuffer> mListener;

    public TimestampedBufferAssembler(int bufferSize, double sampleRate)
    {
        Validate.isTrue(bufferSize % 2 == 0);
        mBuffer = FloatBuffer.allocate(bufferSize);
        mTimestampManager = new SampleTimestampManager(sampleRate);
    }

    /**
     * Updates the reference timestamp to use for all subsequent samples that are assembled.  This timestamp should
     * identify the relative timestamp of the first/next sample that is received by this assembler.
     * @param timestamp to use relative to the incoming sample arrays.
     */
    public void updateTimestamp(long timestamp)
    {
        mTimestampManager.setReferenceTimestamp(timestamp);

        if(mBuffer.position() == 0)
        {
            mCurrentBufferTimestamp = timestamp;
        }
    }

    public void dispose()
    {
        mListener = null;
        mBuffer = null;
    }

    /**
     * Adds the samples to this assembler.  As each buffer is assembled, it will be dispatched to the registered
     * listener.
     *
     * @param samples to assemble
     */
    public void receive(float[] samples)
    {
        if(mBuffer.remaining() >= samples.length)
        {
            mBuffer.put(samples);
            mTimestampManager.increment(samples.length / 2);

            if(!mBuffer.hasRemaining())
            {
                flush();
            }
        }
        else
        {
            int offset = 0;

            while(offset < samples.length)
            {
                int toCopy = mBuffer.remaining();

                if((samples.length - offset) < toCopy)
                {
                    toCopy = samples.length - offset;
                }

                mBuffer.put(samples, offset, toCopy);

                offset += toCopy;
                mTimestampManager.increment(toCopy / 2);

                if(!mBuffer.hasRemaining())
                {
                    flush();
                }
            }
        }
    }

    /**
     * Flushes the current buffer contents to the registered listener
     */
    public void flush()
    {
        if(mListener != null && mBuffer.position() > 0)
        {
            float[] samples = new float[mBuffer.position()];
            mBuffer.rewind();
            mBuffer.get(samples);

            mListener.receive(new TimestampedComplexBuffer(samples, mCurrentBufferTimestamp));
        }

        mBuffer.rewind();
        mCurrentBufferTimestamp = mTimestampManager.getCurrentTimestamp();
    }

    /**
     * Sets the listener to receive the output from this buffer assembler.
     */
    public void setListener(Listener<ComplexBuffer> listener)
    {
        mListener = listener;
    }
}

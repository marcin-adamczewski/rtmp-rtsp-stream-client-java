package net.ossrs.rtmp;

/**
 * the muxed flv frame.
 */
public class SrsFlvFrame {
    // the tag bytes.
    public SrsAllocator.Allocation flvTag;
    // the codec type for audio/aac and video/avc for instance.
    public int avc_aac_type;
    // the frame type, keyframe or not.
    public int frame_type;
    // the tag type, audio, video or data.
    public int type;
    // the dts in ms, tbn is 1000.
    public int dts;

    public boolean is_keyframe() {
        return is_video() && frame_type == SrsFlvMuxer.SrsCodecVideoAVCFrame.KeyFrame;
    }

    public boolean is_sequenceHeader() {
        return avc_aac_type == 0;
    }

    public boolean is_video() {
        return type == SrsFlvMuxer.SrsCodecFlvTag.Video;
    }

    public boolean is_audio() {
        return type == SrsFlvMuxer.SrsCodecFlvTag.Audio;
    }
}
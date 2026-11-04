/*
 * lqt_stub — link-time stub for libquicktime's Windows import library.
 *
 * SR-Septerra's QTML.c decodes the game's QuickTime movies through libquicktime
 * (lqt_open_read, lqt_decode_video, ...). Building real libquicktime for Windows
 * needs MSYS2 + autotools + ffmpeg, so this stub exists only to let the recompiled
 * exe LINK so the rest of the port can be exercised. At runtime every entry point
 * returns 0/NULL: anything that plays a movie will do nothing (and may fail), while
 * the rest of the game is unaffected.
 *
 * All symbols are cdecl in libquicktime, so a definition that ignores its arguments
 * is safe — the caller pops the stack.
 *
 * NOT a substitute for the real library. Ship SR-Septerra.exe linked against this
 * only as a test build.
 */

long lqt_open_read(void)              { return 0; }
long lqt_decode_video(void)           { return 0; }
long lqt_decode_audio_raw(void)       { return 0; }
long lqt_decode_audio_track(void)     { return 0; }
long lqt_frame_time(void)             { return 0; }
long lqt_last_audio_position(void)    { return 0; }
long lqt_get_best_colormodel(void)    { return 0; }
long lqt_get_sample_format(void)      { return 0; }
long lqt_rows_alloc(void)             { return 0; }
long lqt_rows_free(void)              { return 0; }
long lqt_set_cmodel(void)             { return 0; }
long lqt_video_duration(void)         { return 0; }
long lqt_video_time_scale(void)       { return 0; }
long lqt_close(void)                  { return 0; }

/* legacy libquicktime API (Qt/handlers) */
long quicktime_close(void) { return 0; }
long quicktime_audio_length(void) { return 0; }
long quicktime_audio_tracks(void) { return 0; }
long quicktime_video_tracks(void) { return 0; }
long quicktime_sample_rate(void) { return 0; }
long quicktime_track_channels(void) { return 0; }
long quicktime_video_width(void) { return 0; }
long quicktime_video_height(void) { return 0; }
long quicktime_supported_video(void) { return 0; }
long quicktime_supported_audio(void) { return 0; }

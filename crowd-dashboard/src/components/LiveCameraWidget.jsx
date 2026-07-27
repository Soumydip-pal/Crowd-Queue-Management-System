import { useEffect, useRef, useState } from "react";
import { FiCamera, FiSquare, FiVideo } from "react-icons/fi";
import { uploadCameraFrame } from "../services/api";

/**
 * Live crowd counting from any device's browser camera (laptop webcam, phone
 * camera, tablet camera - anything getUserMedia can open). This is different
 * from an actual CCTV/IP camera integration (see ml-service/camera_poller.py
 * for that) - this widget runs entirely in the browser tab that has it open,
 * capturing and submitting a frame on a timer while it's running.
 *
 * Flow: getUserMedia -> <video> preview -> every intervalSeconds, draw the
 * current video frame to an offscreen <canvas> -> canvas.toBlob() -> reuse
 * the same POST /api/camera/count endpoint as the manual single-photo
 * upload, so results show up on the live dashboard identically either way.
 */
export default function LiveCameraWidget({ token, counterId, onResult }) {
  const videoRef = useRef(null);
  const canvasRef = useRef(null);
  const streamRef = useRef(null);
  const intervalRef = useRef(null);

  const [isActive, setIsActive] = useState(false);
  const [intervalSeconds, setIntervalSeconds] = useState(10);
  const [lastCount, setLastCount] = useState(null);
  const [lastUpdatedAt, setLastUpdatedAt] = useState(null);
  const [status, setStatus] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    // Stop the camera automatically if the component unmounts (e.g. the
    // user navigates away) so the browser's camera indicator light turns off.
    return () => stopCamera();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function startCamera() {
    setError("");
    if (!token) {
      setError("Login required to start live camera counting");
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: "environment" }, // prefers the rear/CCTV-facing camera on phones
        audio: false,
      });
      streamRef.current = stream;
      if (videoRef.current) {
        videoRef.current.srcObject = stream;
        await videoRef.current.play();
      }
      setIsActive(true);
      setStatus("Camera live - counting will begin shortly");
      captureAndSubmit(); // first count right away, then on the interval
      intervalRef.current = setInterval(captureAndSubmit, intervalSeconds * 1000);
    } catch (err) {
      setError(
        err.name === "NotAllowedError"
          ? "Camera permission denied - allow camera access in your browser settings"
          : err.message || "Could not access camera"
      );
    }
  }

  function stopCamera() {
    if (intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((track) => track.stop());
      streamRef.current = null;
    }
    setIsActive(false);
    setStatus("");
  }

  async function captureAndSubmit() {
    const video = videoRef.current;
    const canvas = canvasRef.current;
    if (!video || !canvas || video.readyState < 2) {
      return;
    }

    canvas.width = video.videoWidth;
    canvas.height = video.videoHeight;
    const ctx = canvas.getContext("2d");
    ctx.drawImage(video, 0, 0, canvas.width, canvas.height);

    canvas.toBlob(
      async (blob) => {
        if (!blob) return;
        try {
          setStatus("Counting...");
          const file = new File([blob], "live-frame.jpg", { type: "image/jpeg" });
          const result = await uploadCameraFrame({ token, counterId, imageFile: file });
          setLastCount(result.currentLength);
          setLastUpdatedAt(new Date());
          setStatus("Live");
          setError("");
          onResult?.(result);
        } catch (err) {
          setError(err.message || "Camera count failed");
          setStatus("");
        }
      },
      "image/jpeg",
      0.85
    );
  }

  function handleIntervalChange(event) {
    const seconds = Number(event.target.value);
    setIntervalSeconds(seconds);
    if (isActive && intervalRef.current) {
      clearInterval(intervalRef.current);
      intervalRef.current = setInterval(captureAndSubmit, seconds * 1000);
    }
  }

  return (
    <div className="live-camera-widget">
      <div className="live-camera-preview">
        {/* eslint-disable-next-line jsx-a11y/media-has-caption */}
        <video ref={videoRef} muted playsInline className={isActive ? "active" : "hidden"} />
        <canvas ref={canvasRef} style={{ display: "none" }} />
        {!isActive && (
          <div className="live-camera-placeholder">
            <FiVideo />
            <span>Camera is off</span>
          </div>
        )}
      </div>

      <div className="live-camera-controls">
        <label>
          Update every
          <select value={intervalSeconds} onChange={handleIntervalChange}>
            <option value={5}>5 seconds</option>
            <option value={10}>10 seconds</option>
            <option value={30}>30 seconds</option>
            <option value={60}>1 minute</option>
          </select>
        </label>

        {!isActive ? (
          <button type="button" className="btn btn-primary" onClick={startCamera}>
            <FiCamera /> Start Live Camera
          </button>
        ) : (
          <button type="button" className="btn btn-secondary" onClick={stopCamera}>
            <FiSquare /> Stop
          </button>
        )}
      </div>

      {isActive && (
        <p className="muted">
          {status}
          {lastCount !== null && ` - last count: ${lastCount} people`}
          {lastUpdatedAt && ` (${lastUpdatedAt.toLocaleTimeString()})`}
        </p>
      )}
      {error && <p className="admin-message">{error}</p>}
    </div>
  );
}

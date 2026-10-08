import asyncio
import websockets
import mss
import numpy as np
from PIL import Image
import pyautogui
import io
import json

HOST = "0.0.0.0"
PORT = 8765
QUALITY = 50
SCALE = 0.5
FPS_TARGET = 15

pyautogui.FAILSAFE = False
sct = mss.mss()
monitor = sct.monitors[1]

SCREEN_W = monitor["width"]
SCREEN_H = monitor["height"]


def capture_screen():
    img = np.array(sct.grab(monitor))
    img = img[:, :, :3]
    img = img[:, :, ::-1]
    pil = Image.fromarray(img)
    if SCALE != 1.0:
        new_size = (int(pil.width * SCALE), int(pil.height * SCALE))
        pil = pil.resize(new_size, Image.BILINEAR)
    buf = io.BytesIO()
    pil.save(buf, format="JPEG", quality=QUALITY)
    return buf.getvalue()


async def handle_client(websocket):
    print(f"[+] Client terhubung: {websocket.remote_address}")

    async def send_frames():
        try:
            while True:
                frame = capture_screen()
                await websocket.send(frame)
                await asyncio.sleep(1 / FPS_TARGET)
        except websockets.ConnectionClosed:
            pass

    sender_task = asyncio.create_task(send_frames())

    try:
        async for message in websocket:
            if isinstance(message, str):
                try:
                    data = json.loads(message)
                    handle_input(data)
                except Exception as e:
                    print("Input error:", e)
    except websockets.ConnectionClosed:
        pass
    finally:
        sender_task.cancel()
        print(f"[-] Client disconnect: {websocket.remote_address}")


def handle_input(data):
    t = data.get("type")
    if t == "mouse":
        x = max(0, min(SCREEN_W - 1, int(data["x"])))
        y = max(0, min(SCREEN_H - 1, int(data["y"])))
        pyautogui.moveTo(x, y)
    elif t == "click":
        pyautogui.click(button=data.get("button", "left"))
    elif t == "double_click":
        pyautogui.doubleClick()
    elif t == "right_click":
        pyautogui.rightClick()
    elif t == "scroll":
        pyautogui.scroll(int(data.get("amount", 0)))
    elif t == "key":
        if data.get("key"):
            pyautogui.press(data["key"])
    elif t == "text":
        pyautogui.typewrite(data.get("text", ""), interval=0.02)


async def main():
    print(f"[*] Server berjalan di ws://{HOST}:{PORT}")
    print(f"[*] Resolusi layar: {SCREEN_W}x{SCREEN_H}")
    async with websockets.serve(handle_client, HOST, PORT, max_size=10 * 1024 * 1024):
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())

import os
import math
import subprocess
from PIL import Image, ImageDraw, ImageFont

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
IMG_DIR = os.path.join(ROOT_DIR, "docs", "images")
os.makedirs(IMG_DIR, exist_ok=True)

def get_font(size, bold=False, mono=False):
    try:
        if mono:
            path = "C:/Windows/Fonts/consolab.ttf" if bold else "C:/Windows/Fonts/consola.ttf"
        else:
            path = "C:/Windows/Fonts/seguisb.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf"
        if not os.path.exists(path):
            path = "C:/Windows/Fonts/arial.ttf"
        return ImageFont.truetype(path, size)
    except Exception:
        return ImageFont.load_default()

def draw_device_mockup(canvas_w, canvas_h, title, subtitle, render_content_fn):
    img = Image.new("RGB", (canvas_w, canvas_h), (9, 13, 22))
    draw = ImageDraw.Draw(img)

    # Top presentation header
    draw.rectangle([(0, 0), (canvas_w, 120)], fill=(17, 24, 39))
    draw.line([(0, 120), (canvas_w, 120)], fill=(51, 65, 85), width=2)

    font_title = get_font(34, bold=True)
    font_sub = get_font(18)
    font_badge = get_font(16, bold=True, mono=True)

    draw.text((50, 26), title, font=font_title, fill=(248, 250, 252))
    draw.text((50, 74), subtitle, font=font_sub, fill=(148, 163, 184))

    # Badges on top right
    draw.rounded_rectangle([(canvas_w - 420, 38), (canvas_w - 250, 82)], radius=6, fill=(0, 40, 25), outline=(0, 230, 118))
    draw.text((canvas_w - 402, 48), "BLE 5.x CORE", font=font_badge, fill=(0, 230, 118))

    draw.rounded_rectangle([(canvas_w - 230, 38), (canvas_w - 50, 82)], radius=6, fill=(10, 35, 60), outline=(0, 229, 255))
    draw.text((canvas_w - 212, 48), "KOTLIN / COMPOSE", font=font_badge, fill=(0, 229, 255))

    # Phone screen container
    phone_w, phone_h = 760, 1420
    px = (canvas_w - phone_w) // 2
    py = 150

    # Phone outer frame
    draw.rounded_rectangle([(px - 12, py - 12), (px + phone_w + 12, py + phone_h + 12)], radius=40, fill=(17, 24, 39), outline=(51, 65, 85), width=3)
    # Phone display surface
    draw.rounded_rectangle([(px, py), (px + phone_w, py + phone_h)], radius=32, fill=(9, 13, 22))

    # Phone status bar
    draw.text((px + 30, py + 18), "09:41", font=get_font(14, bold=True), fill=(248, 250, 252))
    draw.text((px + phone_w - 140, py + 18), "BLE 5.0  5G  100%", font=get_font(14, bold=True), fill=(248, 250, 252))

    # Dynamic island / camera pill
    draw.rounded_rectangle([(px + (phone_w - 150) // 2, py + 12), (px + (phone_w + 150) // 2, py + 38)], radius=13, fill=(0, 0, 0))

    # Content area inside screen
    content_rect = (px + 16, py + 56, px + phone_w - 16, py + phone_h - 90)
    render_content_fn(draw, content_rect, px, py, phone_w, phone_h)

    # Bottom navigation bar
    nav_y = py + phone_h - 75
    draw.rectangle([(px, nav_y), (px + phone_w, py + phone_h)], fill=(17, 24, 39))
    draw.line([(px, nav_y), (px + phone_w, nav_y)], fill=(51, 65, 85), width=1)

    tabs = ["Scanner", "Explorer", "Scope", "Fuzzer", "Simulator"]
    tab_w = phone_w // len(tabs)
    font_tab = get_font(13, bold=True)
    for i, tab in enumerate(tabs):
        tx = px + i * tab_w + (tab_w - 40) // 2
        col = (0, 229, 255) if i == 0 and "Scanner" in title or i == 1 and "Explorer" in title or i == 2 and "Oscilloscope" in title or i == 3 and "Fuzzer" in title or i == 4 and "Simulator" in title else (100, 116, 139)
        draw.text((tx, nav_y + 36), tab, font=font_tab, fill=col)
        # Dot indicator
        draw.ellipse([(tx + 16, nav_y + 16), (tx + 24, nav_y + 24)], fill=col)

    return img

def render_scanner(draw, rect, px, py, pw, ph):
    font_h = get_font(20, bold=True)
    font_sub = get_font(13)
    font_mono = get_font(12, mono=True)
    font_bold = get_font(14, bold=True)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top search bar
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 44)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 14, y1 + 12), "Filter by name, MAC, or Service UUID...", font=font_sub, fill=(100, 116, 139))

    # Control row
    cy = y1 + 54
    draw.text((x1, cy + 8), "5 peripherals found (Scanning LE 1M/2M channels)", font=font_sub, fill=(148, 163, 184))
    draw.rounded_rectangle([(x2 - 110, cy), (x2, cy + 34)], radius=6, fill=(0, 229, 255))
    draw.text((x2 - 86, cy + 8), "SCANNING", font=font_badge, fill=(0, 0, 0))

    # Devices list
    devices = [
        {
            "name": "ESP32-S3 Waveform Node",
            "mac": "78:21:84:45:9A:10",
            "rssi": "-52 dBm",
            "dist": "0.7m",
            "badges": [("Espressif", (0, 229, 255)), ("NUS UART", (124, 77, 255)), ("LE 2M", (100, 116, 139))],
            "expanded": True
        },
        {
            "name": "Polar H10 3BA244",
            "mac": "C8:FD:19:3B:A2:44",
            "rssi": "-61 dBm",
            "dist": "1.1m",
            "badges": [("Heart Rate", (255, 23, 68)), ("Battery", (0, 230, 118))],
            "expanded": False
        },
        {
            "name": "iBeacon Asset Tag",
            "mac": "E2:C5:6D:B5:D4:F1",
            "rssi": "-66 dBm",
            "dist": "1.4m",
            "badges": [("iBeacon", (255, 179, 0)), ("Apple", (0, 229, 255))],
            "expanded": False
        },
        {
            "name": "RuuviTag CA58",
            "mac": "D4:36:39:CA:58:12",
            "rssi": "-74 dBm",
            "dist": "2.6m",
            "badges": [("Weather Sensor", (0, 230, 118)), ("Ruuvi", (100, 116, 139))],
            "expanded": False
        },
        {
            "name": "IoT Fuzz Target",
            "mac": "DE:AD:BE:EF:00:01",
            "rssi": "-48 dBm",
            "dist": "0.5m",
            "badges": [("Custom Service", (124, 77, 255)), ("Writable", (0, 229, 255))],
            "expanded": False
        }
    ]

    dy = cy + 46
    for dev in devices:
        card_h = 240 if dev["expanded"] else 96
        draw.rounded_rectangle([(x1, dy), (x2, dy + card_h)], radius=10, fill=(17, 24, 39), outline=(0, 229, 255) if dev["expanded"] else (51, 65, 85))

        draw.text((x1 + 16, dy + 14), dev["name"], font=font_bold, fill=(248, 250, 252))
        draw.text((x1 + 16, dy + 36), dev["mac"], font=font_mono, fill=(148, 163, 184))

        # RSSI & Connect
        draw.text((x2 - 190, dy + 16), dev["rssi"], font=font_mono, fill=(0, 230, 118))
        draw.rounded_rectangle([(x2 - 100, dy + 12), (x2 - 16, dy + 44)], radius=6, fill=(0, 229, 255))
        draw.text((x2 - 82, dy + 19), "CONNECT", font=font_badge, fill=(0, 0, 0))

        # Badges
        bx = x1 + 16
        for tag, col in dev["badges"]:
            tw = len(tag) * 8 + 14
            draw.rounded_rectangle([(bx, dy + 62), (bx + tw, dy + 82)], radius=4, fill=(col[0]//6, col[1]//6, col[2]//6), outline=col)
            draw.text((bx + 6, dy + 66), tag, font=font_badge, fill=col)
            bx += tw + 8

        if dev["expanded"]:
            ey = dy + 92
            draw.line([(x1 + 16, ey), (x2 - 16, ey)], fill=(51, 65, 85), width=1)
            draw.text((x1 + 16, ey + 8), "Primary PHY: LE 1M | Secondary PHY: LE 2M | Distance: ~0.7m", font=font_mono, fill=(148, 163, 184))
            draw.text((x1 + 16, ey + 26), "Service UUID: 6E400001-B5A3-F393-E0A9-E50E24DCCA9E (NUS)", font=font_mono, fill=(124, 77, 255))
            draw.text((x1 + 16, ey + 44), "Raw AD Hex Payload (31 bytes):", font=font_mono, fill=(248, 250, 252))

            # Hex dump preview
            draw.rounded_rectangle([(x1 + 16, ey + 64), (x2 - 16, ey + 134)], radius=6, fill=(9, 13, 22), outline=(51, 65, 85))
            draw.text((x1 + 24, ey + 72), "0000: 02 01 06 11 07 9E CA DC 24 0E E5 A9 E0 93 F3 A3  |  ................", font=font_mono, fill=(0, 229, 255))
            draw.text((x1 + 24, ey + 90), "0010: B5 01 00 40 6E 07 FF E5 02 45 53 50 33 32 01     |  ...@n...ESP32.", font=font_mono, fill=(0, 229, 255))

        dy += card_h + 10

def render_explorer(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_sub = get_font(13)
    font_mono = get_font(12, mono=True)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Header Card
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 94)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, y1 + 14), "ESP32-S3 Waveform Node", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 36), "78:21:84:45:9A:10", font=font_mono, fill=(148, 163, 184))

    # Connection params
    draw.rounded_rectangle([(x1 + 16, y1 + 60), (x1 + 120, y1 + 82)], radius=4, fill=(0, 50, 20), outline=(0, 230, 118))
    draw.text((x1 + 24, y1 + 64), "CONNECTED", font=font_badge, fill=(0, 230, 118))

    draw.rounded_rectangle([(x1 + 130, y1 + 60), (x1 + 260, y1 + 82)], radius=4, fill=(9, 13, 22), outline=(0, 229, 255))
    draw.text((x1 + 138, y1 + 64), "MTU: 517 BYTES", font=font_badge, fill=(0, 229, 255))

    draw.rounded_rectangle([(x1 + 270, y1 + 60), (x1 + 380, y1 + 82)], radius=4, fill=(9, 13, 22), outline=(124, 77, 255))
    draw.text((x1 + 278, y1 + 64), "PHY: LE 2M", font=font_badge, fill=(124, 77, 255))

    draw.rounded_rectangle([(x2 - 120, y1 + 14), (x2 - 16, y1 + 44)], radius=6, fill=(255, 23, 68))
    draw.text((x2 - 108, y1 + 20), "DISCONNECT", font=font_badge, fill=(255, 255, 255))

    # Services
    sy = y1 + 106
    draw.text((x1, sy), "Discovered GATT Services (2):", font=font_bold, fill=(248, 250, 252))
    sy += 26

    # Service 1: Nordic UART Service
    draw.rounded_rectangle([(x1, sy), (x2, sy + 380)], radius=10, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, sy + 14), "Nordic UART Service (NUS)", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, sy + 34), "6E400001-B5A3-F393-E0A9-E50E24DCCA9E", font=font_mono, fill=(100, 116, 139))

    # Characteristic 1: RX (Write)
    cy = sy + 60
    draw.rounded_rectangle([(x1 + 12, cy), (x2 - 12, cy + 130)], radius=6, fill=(30, 41, 59))
    draw.text((x1 + 22, cy + 12), "Nordic UART RX (Write)", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 22, cy + 32), "6E400002-B5A3-F393-E0A9-E50E24DCCA9E", font=font_mono, fill=(148, 163, 184))
    draw.text((x2 - 160, cy + 12), "WRITE | WRITE_NO_RESP", font=font_badge, fill=(0, 229, 255))

    draw.rounded_rectangle([(x1 + 22, cy + 54), (x2 - 22, cy + 86)], radius=4, fill=(9, 13, 22))
    draw.text((x1 + 30, cy + 62), "Value: 41 43 4B 3A 20 4F 4B 00  (\"ACK: OK\\0\")", font=font_mono, fill=(0, 229, 255))

    draw.rounded_rectangle([(x1 + 22, cy + 94), (x1 + 100, cy + 122)], radius=4, fill=(0, 229, 255))
    draw.text((x1 + 38, cy + 100), "WRITE", font=font_badge, fill=(0, 0, 0))

    # Characteristic 2: TX (Notify)
    cy += 142
    draw.rounded_rectangle([(x1 + 12, cy), (x2 - 12, cy + 160)], radius=6, fill=(30, 41, 59))
    draw.text((x1 + 22, cy + 12), "Nordic UART TX (Notify Stream)", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 22, cy + 32), "6E400003-B5A3-F393-E0A9-E50E24DCCA9E", font=font_mono, fill=(148, 163, 184))
    draw.text((x2 - 110, cy + 12), "NOTIFY (ACTIVE)", font=font_badge, fill=(0, 230, 118))

    draw.rounded_rectangle([(x1 + 22, cy + 54), (x2 - 22, cy + 116)], radius=4, fill=(9, 13, 22))
    draw.text((x1 + 30, cy + 62), "HEX: 00 00 20 41 00 00 48 42 00 00 1C 41", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 30, cy + 80), "3-Channel Float32: X=10.00 | Y=50.00 | Z=9.75", font=font_mono, fill=(248, 250, 252))
    draw.text((x1 + 30, cy + 98), "Frequency: 10 Hz Telemetry Stream", font=font_mono, fill=(100, 116, 139))

    draw.rounded_rectangle([(x1 + 22, cy + 124), (x1 + 150, cy + 152)], radius=4, fill=(124, 77, 255))
    draw.text((x1 + 34, cy + 130), "ROUTE TO SCOPE", font=font_badge, fill=(255, 255, 255))

    # Service 2: Battery
    sy += 394
    draw.rounded_rectangle([(x1, sy), (x2, sy + 150)], radius=10, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, sy + 14), "Battery Service (0x180F)", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, sy + 34), "0000180F-0000-1000-8000-00805F9B34FB", font=font_mono, fill=(100, 116, 139))
    draw.text((x1 + 16, sy + 64), "Battery Level (0x2A19): 94% (Read, Notify)", font=font_bold, fill=(0, 230, 118))

def render_oscilloscope(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_sub = get_font(13)
    font_mono = get_font(12, mono=True)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Control Bar
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 70)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, y1 + 14), "Real-Time Waveform Oscilloscope", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Stream: 3-Axis Accelerometer (X, Y, Z Float32)", font=font_mono, fill=(0, 229, 255))

    draw.rounded_rectangle([(x2 - 170, y1 + 18), (x2 - 96, y1 + 52)], radius=6, fill=(0, 229, 255))
    draw.text((x2 - 156, y1 + 26), "LIVE 10Hz", font=font_badge, fill=(0, 0, 0))

    draw.rounded_rectangle([(x2 - 86, y1 + 18), (x2 - 16, y1 + 52)], radius=6, fill=(30, 41, 59), outline=(51, 65, 85))
    draw.text((x2 - 70, y1 + 26), "PAUSE", font=font_badge, fill=(248, 250, 252))

    # Canvas Area
    cy = y1 + 84
    ch = 540
    draw.rounded_rectangle([(x1, cy), (x2, cy + ch)], radius=8, fill=(4, 6, 10), outline=(51, 65, 85))

    # Oscilloscope Grid
    for gx in range(1, 8):
        x = x1 + (x2 - x1) * gx // 8
        draw.line([(x, cy), (x, cy + ch)], fill=(22, 32, 50), width=1)
    for gy in range(1, 6):
        y = cy + ch * gy // 6
        draw.line([(x1, y), (x2, y)], fill=(22, 32, 50), width=1)

    # Center baseline
    mid_y = cy + ch // 2
    draw.line([(x1, mid_y), (x2, mid_y)], fill=(38, 56, 84), width=2)
    draw.text((x1 + 10, mid_y - 18), "0.00", font=font_mono, fill=(100, 116, 139))
    draw.text((x1 + 10, cy + 14), "+100.0", font=font_mono, fill=(100, 116, 139))
    draw.text((x1 + 10, cy + ch - 26), "-100.0", font=font_mono, fill=(100, 116, 139))

    # Draw waves
    # Wave 1 (Cyan): Sine wave X
    pts_x = []
    pts_y = []
    pts_z = []
    steps = 120
    for i in range(steps):
        px_i = x1 + (x2 - x1) * i // steps
        val_x = math.sin(i * 0.18) * 160 + math.sin(i * 0.05) * 40
        val_y = math.cos(i * 0.15) * 120
        val_z = math.sin(i * 0.35) * 60

        pts_x.append((px_i, mid_y - int(val_x)))
        pts_y.append((px_i, mid_y - int(val_y)))
        pts_z.append((px_i, mid_y - int(val_z)))

    draw.line(pts_x, fill=(0, 229, 255), width=3)
    draw.line(pts_y, fill=(255, 61, 0), width=2)
    draw.line(pts_z, fill=(0, 230, 118), width=2)

    # Telemetry Channel Cards below scope
    ty = cy + ch + 16
    draw.text((x1, ty), "Telemetry Channel Metrics:", font=font_bold, fill=(248, 250, 252))
    ty += 24

    cards = [
        ("Axis X", "+78.40", "-98.2", "+99.5", (0, 229, 255)),
        ("Axis Y", "-12.60", "-79.1", "+80.0", (255, 61, 0)),
        ("Axis Z", "+09.81", "-38.4", "+39.2", (0, 230, 118))
    ]
    cw = (x2 - x1 - 20) // 3
    for i, (name, cur, mn, mx, col) in enumerate(cards):
        cx = x1 + i * (cw + 10)
        draw.rounded_rectangle([(cx, ty), (cx + cw, ty + 96)], radius=8, fill=(17, 24, 39), outline=col)
        draw.text((cx + 12, ty + 10), name, font=font_bold, fill=col)
        draw.text((cx + 12, ty + 34), cur, font=get_font(20, bold=True, mono=True), fill=(248, 250, 252))
        draw.text((cx + 12, ty + 68), f"Min: {mn} | Max: {mx}", font=font_mono, fill=(148, 163, 184))

def render_fuzzer(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_sub = get_font(13)
    font_mono = get_font(12, mono=True)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Control Bar
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 110)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, y1 + 14), "IoT Peripheral Security Fuzzer", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 36), "Target: DE:AD:BE:EF:00:01 (Vulnerable Sensor)", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 54), "Strategy: Boundary Value & Buffer Overflow Injection", font=font_sub, fill=(124, 77, 255))
    draw.text((x1 + 16, y1 + 76), "Progress: 10 / 10 Tests Completed (1 Anomaly Detected)", font=font_mono, fill=(255, 23, 68))

    draw.rounded_rectangle([(x2 - 130, y1 + 16), (x2 - 16, y1 + 50)], radius=6, fill=(124, 77, 255))
    draw.text((x2 - 116, y1 + 24), "RE-RUN FUZZ", font=font_badge, fill=(255, 255, 255))

    # Anomaly Alert Box
    ay = y1 + 124
    draw.rounded_rectangle([(x1, ay), (x2, ay + 64)], radius=8, fill=(40, 10, 15), outline=(255, 23, 68))
    draw.text((x1 + 16, ay + 12), "CRITICAL FAULT DETECTED", font=font_bold, fill=(255, 23, 68))
    draw.text((x1 + 16, ay + 36), "Test #10: Buffer overflow fault on 600-byte write (GATT_INVALID_ATTRIBUTE_LENGTH)", font=font_mono, fill=(248, 250, 252))

    # Fuzz Test Case Log Table
    ty = ay + 80
    draw.text((x1, ty), "Execution Audit Trail:", font=font_bold, fill=(248, 250, 252))
    ty += 24

    cases = [
        ("Zero Length Buffer (0 B)", "00 B", "SUCCESS", "15ms", "NORMAL", (0, 230, 118)),
        ("Single Byte 0x00", "01 B", "SUCCESS", "18ms", "NORMAL", (0, 230, 118)),
        ("Single Byte 0xFF", "01 B", "SUCCESS", "16ms", "NORMAL", (0, 230, 118)),
        ("Default BLE MTU (20 B)", "20 B", "SUCCESS", "22ms", "NORMAL", (0, 230, 118)),
        ("Standard ATT MTU (23 B)", "23 B", "SUCCESS", "24ms", "NORMAL", (0, 230, 118)),
        ("High Speed MTU (247 B)", "247 B", "SUCCESS", "48ms", "NORMAL", (0, 230, 118)),
        ("Max ATT MTU (512 B)", "512 B", "SUCCESS", "52ms", "NORMAL", (0, 230, 118)),
        ("Max Android MTU (517 B)", "517 B", "SUCCESS", "54ms", "NORMAL", (0, 230, 118)),
        ("Format String (%s%n%x)", "32 B", "SUCCESS", "25ms", "NORMAL", (0, 230, 118)),
        ("Oversized Buffer (600 B)", "600 B", "FAILURE", "50ms", "ANOMALY", (255, 23, 68))
    ]

    for title, sz, status, lat, tag, col in cases:
        draw.rounded_rectangle([(x1, ty), (x2, ty + 46)], radius=6, fill=(17, 24, 39), outline=col if tag == "ANOMALY" else (51, 65, 85))
        draw.text((x1 + 14, ty + 8), title, font=font_bold, fill=(248, 250, 252))
        draw.text((x1 + 14, ty + 26), f"Payload: {sz} | Latency: {lat} | Status: {status}", font=font_mono, fill=(148, 163, 184))

        draw.rounded_rectangle([(x2 - 90, ty + 10), (x2 - 14, ty + 36)], radius=4, fill=(col[0]//6, col[1]//6, col[2]//6), outline=col)
        draw.text((x2 - 80, ty + 16), tag, font=font_badge, fill=col)
        ty += 52

def render_simulator(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_sub = get_font(13)
    font_mono = get_font(12, mono=True)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Control Bar
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 100)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, y1 + 14), "Virtual GATT Server Simulator", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 36), "Emulated Profile: Heart Rate Sensor (0x180D)", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 54), "Advertising: \"BLEPulse Heart Rate Monitor\" | Connectable", font=font_sub, fill=(148, 163, 184))

    draw.rounded_rectangle([(x1 + 16, y1 + 72), (x1 + 140, y1 + 94)], radius=4, fill=(0, 50, 20), outline=(0, 230, 118))
    draw.text((x1 + 24, y1 + 76), "SERVER ACTIVE", font=font_badge, fill=(0, 230, 118))

    draw.rounded_rectangle([(x2 - 120, y1 + 18), (x2 - 16, y1 + 52)], radius=6, fill=(255, 23, 68))
    draw.text((x2 - 108, y1 + 26), "STOP SERVER", font=font_badge, fill=(255, 255, 255))

    # Profile Characteristics
    sy = y1 + 116
    draw.text((x1, sy), "Emulated GATT Services & Characteristics:", font=font_bold, fill=(248, 250, 252))
    sy += 24

    chars = [
        ("Heart Rate Measurement (0x2A37)", "Dynamic Cardiac Rhythm (60-120 BPM)", "00 48", "72 BPM (HRV sinusoidal)", (255, 23, 68)),
        ("Body Sensor Location (0x2A38)", "Static Value", "01", "Chest Location", (0, 229, 255)),
        ("Battery Level (0x180F -> 0x2A19)", "Discharging Battery Simulation", "5E", "94% Remaining", (0, 230, 118))
    ]

    for name, beh, raw, desc, col in chars:
        draw.rounded_rectangle([(x1, sy), (x2, sy + 88)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
        draw.text((x1 + 16, sy + 12), name, font=font_bold, fill=(248, 250, 252))
        draw.text((x1 + 16, sy + 34), f"Behavior: {beh}", font=font_mono, fill=(124, 77, 255))

        draw.rounded_rectangle([(x1 + 16, sy + 54), (x2 - 16, sy + 80)], radius=4, fill=(9, 13, 22))
        draw.text((x1 + 24, sy + 60), f"Current Byte Value: {raw}  ({desc})", font=font_mono, fill=col)
        sy += 98

    # Activity Log
    draw.text((x1, sy + 10), "Live GATT Server Request Log:", font=font_bold, fill=(248, 250, 252))
    sy += 34

    logs = [
        "09:41:22 - Central 5C:02:72:11:AA:BB connected (MTU negotiated: 517)",
        "09:41:23 - CCCD subscription enabled for Heart Rate (0x2A37)",
        "09:41:24 - Dispatched Notification: 0x00 0x48 (72 BPM)",
        "09:41:25 - Dispatched Notification: 0x00 0x4B (75 BPM)",
        "09:41:26 - Dispatched Notification: 0x00 0x4A (74 BPM)",
        "09:41:27 - Read request on Body Sensor Location (0x2A38) -> returned 0x01"
    ]
    draw.rounded_rectangle([(x1, sy), (x2, sy + 170)], radius=8, fill=(9, 13, 22), outline=(51, 65, 85))
    for i, log in enumerate(logs):
        draw.text((x1 + 16, sy + 12 + i * 24), f"• {log}", font=font_mono, fill=(148, 163, 184))

def main():
    print("Generating high-fidelity UI screenshots...")

    s1 = draw_device_mockup(1200, 1650, "BLEPulse - Real-Time Advertising Scanner", "Scans BLE 5.x broadcast channels, decodes iBeacon & Eddystone, and plots RSSI proximity", render_scanner)
    s1.save(os.path.join(IMG_DIR, "blepulse_scanner.png"))
    print("[OK] blepulse_scanner.png")

    s2 = draw_device_mockup(1200, 1650, "BLEPulse - Interactive GATT Explorer", "Service and characteristic browser with MTU negotiation, PHY switching, and type decoders", render_explorer)
    s2.save(os.path.join(IMG_DIR, "blepulse_explorer.png"))
    print("[OK] blepulse_explorer.png")

    s3 = draw_device_mockup(1200, 1650, "BLEPulse - Live Waveform Oscilloscope", "Hardware-accelerated 60fps real-time waveform plotter for Notify & Indicate telemetry streams", render_oscilloscope)
    s3.save(os.path.join(IMG_DIR, "blepulse_oscilloscope.png"))
    print("[OK] blepulse_oscilloscope.png")

    s4 = draw_device_mockup(1200, 1650, "BLEPulse - IoT Peripheral Fuzzer", "Stress-tests microcontrollers and GATT servers with boundary values, mutations, and overflow probes", render_fuzzer)
    s4.save(os.path.join(IMG_DIR, "blepulse_fuzzer.png"))
    print("[OK] blepulse_fuzzer.png")

    s5 = draw_device_mockup(1200, 1650, "BLEPulse - Virtual Peripheral Simulator", "Turns Android phone into an emulated GATT peripheral server to test companion apps and firmware", render_simulator)
    s5.save(os.path.join(IMG_DIR, "blepulse_simulator.png"))
    print("[OK] blepulse_simulator.png")

    # Generate Animated Demo GIF using FFmpeg
    print("Generating animated demo GIF (blepulse_demo.gif)...")
    frames = [s1, s2, s3, s4, s5]
    frame_paths = []
    for i, f in enumerate(frames):
        p = os.path.join(IMG_DIR, f"temp_frame_{i}.png")
        f.resize((800, 1100), Image.Resampling.LANCZOS).save(p)
        frame_paths.append(p)

    gif_path = os.path.join(IMG_DIR, "blepulse_demo.gif")
    # Build GIF with ffmpeg with 1.8 second delay per frame
    cmd = [
        "ffmpeg", "-y",
        "-framerate", "0.6",
        "-i", os.path.join(IMG_DIR, "temp_frame_%d.png"),
        "-vf", "split[s0][s1];[s0]palettegen[p];[s1][p]paletteuse",
        gif_path
    ]
    subprocess.run(cmd, check=True)

    for p in frame_paths:
        try: os.remove(p)
        except: pass
    print("[OK] blepulse_demo.gif created successfully!")

    # Generate Logo strictly for README header
    logo = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    ldraw = ImageDraw.Draw(logo)
    # Squircle background
    ldraw.rounded_rectangle([(24, 24), (488, 488)], radius=110, fill=(11, 15, 25), outline=(0, 229, 255), width=8)

    # Bluetooth rune + pulse wave stylized mark
    # Draw pulse wave line
    wave = [
        (80, 256), (160, 256), (190, 210), (220, 310), (256, 120),
        (292, 380), (322, 230), (352, 280), (380, 256), (432, 256)
    ]
    ldraw.line(wave, fill=(0, 229, 255), width=10)

    # Center Bluetooth B rune
    # Vertical stem
    ldraw.line([(256, 140), (256, 372)], fill=(255, 255, 255), width=12)
    # Cross arms
    ldraw.line([(200, 196), (312, 316)], fill=(255, 255, 255), width=12)
    ldraw.line([(200, 316), (312, 196)], fill=(255, 255, 255), width=12)
    ldraw.line([(312, 196), (256, 140)], fill=(255, 255, 255), width=12)
    ldraw.line([(312, 316), (256, 372)], fill=(255, 255, 255), width=12)

    logo.save(os.path.join(IMG_DIR, "blepulse_logo.png"))
    print("[OK] blepulse_logo.png created for README header")

if __name__ == "__main__":
    main()

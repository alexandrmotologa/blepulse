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

    tabs = ["Scanner", "Explorer", "Scope", "Tools", "Simulator"]
    tab_w = phone_w // len(tabs)
    font_tab = get_font(13, bold=True)
    for i, tab in enumerate(tabs):
        tx = px + i * tab_w + (tab_w - 40) // 2
        is_sel = (i == 0 and "Scanner" in title) or \
                 (i == 1 and "Explorer" in title) or \
                 (i == 2 and ("Oscilloscope" in title or "FFT" in title)) or \
                 (i == 3 and any(w in title for w in ["Terminal", "Geiger", "Audit", "Macro", "FOTA", "Fuzzer"])) or \
                 (i == 4 and "Simulator" in title)
        col = (0, 229, 255) if is_sel else (100, 116, 139)
        draw.text((tx, nav_y + 36), tab, font=font_tab, fill=col)
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

    # Device list items
    items = [
        {
            "name": "Nordic_HRM_Sensor", "addr": "E0:4E:01:23:45:67", "rssi": -48, "dist": "0.6m",
            "badges": [("Nordic Semi", (0, 229, 255)), ("Heart Rate 0x180D", (0, 230, 118)), ("LE 2M", (100, 116, 139))]
        },
        {
            "name": "ESP32-S3 Environment Station", "addr": "24:0A:C4:88:99:AA", "rssi": -62, "dist": "1.8m",
            "badges": [("Espressif", (0, 229, 255)), ("Env Sensing 0x181A", (0, 230, 118)), ("Connectable", (0, 230, 118))]
        },
        {
            "name": "iBeacon Proximity Beacon", "addr": "58:B0:35:F1:C2:10", "rssi": -71, "dist": "3.4m",
            "badges": [("iBeacon", (255, 179, 0)), ("Major: 100", (148, 163, 184)), ("Minor: 204", (148, 163, 184))]
        },
        {
            "name": "Eddystone-URL Broadcaster", "addr": "AC:23:3F:12:44:88", "rssi": -79, "dist": "5.1m",
            "badges": [("Eddystone", (124, 77, 255)), ("URL Frame", (0, 229, 255)), ("TLM Telemetry", (0, 230, 118))]
        },
        {
            "name": "Smart Door Lock v2", "addr": "B4:52:7E:90:11:33", "rssi": -85, "dist": "7.8m",
            "badges": [("TI CC2640", (0, 229, 255)), ("Secured GATT", (255, 23, 68)), ("Encrypted", (255, 179, 0))]
        }
    ]

    iy = cy + 44
    for it in items:
        # Card background
        draw.rounded_rectangle([(x1, iy), (x2, iy + 130)], radius=10, fill=(17, 24, 39), outline=(51, 65, 85))

        # Title & MAC
        draw.text((x1 + 16, iy + 14), it["name"], font=font_bold, fill=(248, 250, 252))
        draw.text((x1 + 16, iy + 36), it["addr"], font=font_mono, fill=(148, 163, 184))

        # RSSI & Proximity
        draw.text((x2 - 100, iy + 14), f"{it['rssi']} dBm", font=font_mono, fill=(0, 230, 118) if it["rssi"] > -60 else (0, 229, 255))
        draw.text((x2 - 100, iy + 36), it["dist"], font=font_mono, fill=(100, 116, 139))

        # Mini sparkline
        sp_x = x2 - 190
        sp_y = iy + 24
        draw.line([(sp_x, sp_y + 10), (sp_x + 15, sp_y + 5), (sp_x + 30, sp_y + 12), (sp_x + 45, sp_y + 2), (sp_x + 60, sp_y + 8)], fill=(0, 229, 255), width=2)

        # Badges row
        bx = x1 + 16
        for label, col in it["badges"]:
            b_w = len(label) * 8 + 14
            draw.rounded_rectangle([(bx, iy + 66), (bx + b_w, iy + 90)], radius=4, fill=(10, 25, 40), outline=col)
            draw.text((bx + 7, iy + 71), label, font=font_badge, fill=col)
            bx += b_w + 8

        # Connect button
        draw.rounded_rectangle([(x2 - 110, iy + 84), (x2 - 14, iy + 118)], radius=6, fill=(0, 229, 255))
        draw.text((x2 - 96, iy + 94), "CONNECT", font=font_badge, fill=(0, 0, 0))

        iy += 142

def render_explorer(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Device header banner
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 90)], radius=10, fill=(17, 24, 39), outline=(0, 229, 255))
    draw.text((x1 + 16, y1 + 14), "Nordic_HRM_Sensor", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Connected (MTU: 247 | PHY: 2M High-Speed | Latency: 12ms)", font=font_sub, fill=(0, 230, 118))
    draw.text((x1 + 16, y1 + 60), "MAC: E0:4E:01:23:45:67 | Battery: 94% (3.95V)", font=font_mono, fill=(148, 163, 184))

    # Services
    services = [
        {
            "name": "Heart Rate Service (0x180D)",
            "uuid": "0000180d-0000-1000-8000-00805f9b34fb",
            "chars": [
                {
                    "name": "Heart Rate Measurement", "uuid": "00002a37-0000-1000-8000-00805f9b34fb",
                    "props": "NOTIFY | INDICATE", "val": "74 BPM (RR: 812ms)", "hex": "0x16 0x4A 0x2C 0x03"
                },
                {
                    "name": "Body Sensor Location", "uuid": "00002a38-0000-1000-8000-00805f9b34fb",
                    "props": "READ", "val": "Chest (Position 0x01)", "hex": "0x01"
                }
            ]
        },
        {
            "name": "Nordic UART Serial Service (NUS)",
            "uuid": "6e400001-b5a3-f393-e0a9-e50e24dcca9e",
            "chars": [
                {
                    "name": "NUS RX Characteristic", "uuid": "6e400002-b5a3-f393-e0a9-e50e24dcca9e",
                    "props": "WRITE | WRITE_NO_RESP", "val": "\"AT+STATUS=OK\\r\\n\"", "hex": "0x41 0x54 0x2B 0x53"
                },
                {
                    "name": "NUS TX Characteristic", "uuid": "6e400003-b5a3-f393-e0a9-e50e24dcca9e",
                    "props": "NOTIFY", "val": "\"OK\\r\\n\"", "hex": "0x4F 0x4B 0x0D 0x0A"
                }
            ]
        },
        {
            "name": "Battery Service (0x180F)",
            "uuid": "0000180f-0000-1000-8000-00805f9b34fb",
            "chars": [
                {
                    "name": "Battery Level", "uuid": "00002a19-0000-1000-8000-00805f9b34fb",
                    "props": "READ | NOTIFY", "val": "94%", "hex": "0x5E"
                }
            ]
        }
    ]

    sy = y1 + 104
    for svc in services:
        draw.rounded_rectangle([(x1, sy), (x2, sy + 38)], radius=6, fill=(30, 41, 59))
        draw.text((x1 + 14, sy + 10), svc["name"], font=font_bold, fill=(0, 229, 255))

        cy = sy + 44
        for ch in svc["chars"]:
            draw.rounded_rectangle([(x1 + 10, cy), (x2, cy + 96)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
            draw.text((x1 + 24, cy + 10), ch["name"], font=font_bold, fill=(248, 250, 252))
            draw.text((x1 + 24, cy + 30), ch["uuid"], font=font_mono, fill=(100, 116, 139))

            draw.rounded_rectangle([(x2 - 170, cy + 8), (x2 - 12, cy + 32)], radius=4, fill=(10, 35, 60))
            draw.text((x2 - 162, cy + 12), ch["props"], font=font_badge, fill=(0, 229, 255))

            draw.text((x1 + 24, cy + 54), f"Parsed: {ch['val']}", font=font_mono, fill=(0, 230, 118))
            draw.text((x1 + 24, cy + 74), f"Raw Hex: {ch['hex']}", font=font_mono, fill=(148, 163, 184))

            # Action pills
            draw.rounded_rectangle([(x2 - 80, cy + 58), (x2 - 14, cy + 86)], radius=4, fill=(51, 65, 85))
            draw.text((x2 - 68, cy + 64), "READ", font=font_badge, fill=(248, 250, 252))

            cy += 104
        sy = cy + 10

def render_oscilloscope(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)

    x1, y1, x2, y2 = rect

    # Controls
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 54)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 14, y1 + 16), "Stream: Heart Rate (BPM) + 3-Axis Accel", font=font_bold, fill=(248, 250, 252))

    draw.rounded_rectangle([(x2 - 120, y1 + 10), (x2 - 12, y1 + 44)], radius=6, fill=(0, 230, 118))
    draw.text((x2 - 100, y1 + 18), "LIVE 60 FPS", font=get_font(11, bold=True, mono=True), fill=(0, 0, 0))

    # Canvas Scope Area
    scope_y1 = y1 + 64
    scope_y2 = scope_y1 + 420
    draw.rounded_rectangle([(x1, scope_y1), (x2, scope_y2)], radius=12, fill=(4, 6, 10), outline=(51, 65, 85))

    # Grid lines
    for i in range(1, 8):
        gx = x1 + (x2 - x1) * i // 8
        draw.line([(gx, scope_y1), (gx, scope_y2)], fill=(22, 32, 50), width=1)
    for j in range(1, 6):
        gy = scope_y1 + (scope_y2 - scope_y1) * j // 6
        draw.line([(x1, gy), (x2, gy)], fill=(22, 32, 50), width=1)

    # Center baseline
    mid_y = (scope_y1 + scope_y2) // 2
    draw.line([(x1, mid_y), (x2, mid_y)], fill=(38, 56, 84), width=2)

    # Waveform 1: ECG / Pulse (Red/Coral)
    pts1 = []
    width = x2 - x1
    for step in range(0, width, 4):
        x = x1 + step
        t = (step % 240)
        if t < 60:
            y = mid_y - 20 + math.sin(t * 0.1) * 8
        elif t < 80:
            y = mid_y - 15 - math.sin((t - 60) * 0.15) * 15 # P wave
        elif t < 100:
            y = mid_y - 10
        elif t < 110:
            y = mid_y + 20 # Q dip
        elif t < 125:
            y = mid_y - 120 # R peak!
        elif t < 140:
            y = mid_y + 40 # S dip
        elif t < 180:
            y = mid_y - 35 - math.sin((t - 140) * 0.08) * 30 # T wave
        else:
            y = mid_y - 20
        pts1.append((x, y))

    for k in range(len(pts1) - 1):
        draw.line([pts1[k], pts1[k + 1]], fill=(255, 61, 0), width=3)

    # Waveform 2: Accelerometer magnitude (Cyan)
    pts2 = []
    for step in range(0, width, 4):
        x = x1 + step
        y = mid_y + 80 + math.sin(step * 0.04) * 45 + math.cos(step * 0.12) * 15
        pts2.append((x, y))

    for k in range(len(pts2) - 1):
        draw.line([pts2[k], pts2[k + 1]], fill=(0, 229, 255), width=2)

    # Telemetry metrics row
    my = scope_y2 + 16
    cards = [
        {"name": "Heart Rate", "val": "74 BPM", "sub": "Min: 68 | Max: 82", "col": (255, 61, 0)},
        {"name": "Accel Magnitude", "val": "1.02 G", "sub": "X:0.04 Y:0.98 Z:0.2", "col": (0, 229, 255)},
        {"name": "Sampling Rate", "val": "50.0 Hz", "sub": "Packet: 20ms", "col": (0, 230, 118)}
    ]
    cw = (x2 - x1 - 20) // 3
    for i, c in enumerate(cards):
        cx = x1 + i * (cw + 10)
        draw.rounded_rectangle([(cx, my), (cx + cw, my + 90)], radius=8, fill=(17, 24, 39), outline=c["col"])
        draw.text((cx + 12, my + 10), c["name"], font=font_sub, fill=c["col"])
        draw.text((cx + 12, my + 34), c["val"], font=get_font(20, bold=True, mono=True), fill=(248, 250, 252))
        draw.text((cx + 12, my + 64), c["sub"], font=font_mono, fill=(148, 163, 184))

def render_fuzzer(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)

    x1, y1, x2, y2 = rect

    # Fuzzer config card
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 120)], radius=10, fill=(17, 24, 39), outline=(255, 23, 68))
    draw.text((x1 + 16, y1 + 14), "GATT Vulnerability & Boundary Fuzzer", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Target: 0000ffe1-0000-1000-8000-00805f9b34fb (Serial RX)", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 60), "Mutation Engine: Boundary Ints + Buffer Overflow (512B) + Format Strings", font=font_sub, fill=(148, 163, 184))
    draw.text((x1 + 16, y1 + 86), "Probing Mode: Continuous Randomized Mutation | Interval: 25ms", font=font_mono, fill=(255, 179, 0))

    # Stats row
    fy = y1 + 134
    stats = [
        ("Packets Sent", "1,420", (0, 229, 255)),
        ("Crashes / Disconnects", "2 DETECTED", (255, 23, 68)),
        ("Anomalies", "14 (Timeout)", (255, 179, 0)),
        ("Current Speed", "40 pkt/sec", (0, 230, 118))
    ]
    sw = (x2 - x1 - 30) // 4
    for i, (k, v, col) in enumerate(stats):
        sx = x1 + i * (sw + 10)
        draw.rounded_rectangle([(sx, fy), (sx + sw, fy + 70)], radius=6, fill=(17, 24, 39), outline=(51, 65, 85))
        draw.text((sx + 8, fy + 8), k, font=get_font(10), fill=(148, 163, 184))
        draw.text((sx + 8, fy + 32), v, font=get_font(13, bold=True, mono=True), fill=col)

    # Execution Console
    cy = fy + 82
    draw.rounded_rectangle([(x1, cy), (x2, cy + 300)], radius=8, fill=(4, 6, 10), outline=(51, 65, 85))
    draw.text((x1 + 14, cy + 12), "LIVE PROBE AUDIT LOG", font=get_font(12, bold=True, mono=True), fill=(0, 229, 255))

    logs = [
        ("[09:41:02.102] TX BOUNDARY_INT_32: 0x7FFFFFFF (2147483647) -> ACK 0x00 (Success)", (148, 163, 184)),
        ("[09:41:02.128] TX BOUNDARY_INT_32: 0x80000000 (-2147483648) -> ACK 0x00 (Success)", (148, 163, 184)),
        ("[09:41:02.155] TX OVERFLOW_STRING: 'A'*256 bytes -> GATT_STATUS_SUCCESS", (148, 163, 184)),
        ("[09:41:02.180] TX OVERFLOW_STRING: 'A'*512 bytes -> GATT_ERROR (0x85) Buffer Truncation", (255, 179, 0)),
        ("[09:41:02.210] TX FORMAT_STRING: '%x%x%s%n%p' -> DEVICE UNRESPONSIVE (Timeout 2000ms)", (255, 23, 68)),
        ("[09:41:04.215] ANOMALY DETECTED: GattCallback timed out on handle 0x002A", (255, 23, 68)),
        ("[09:41:04.230] TX RECONNECT_PROBE: Peripheral auto-rebooted with Watchdog Reset!", (0, 230, 118)),
        ("[09:41:04.300] TX BIT_FLIP_MUTATION: 0xFF 0x00 0xAA 0x55 -> ACK 0x00", (148, 163, 184))
    ]
    for i, (line, col) in enumerate(logs):
        draw.text((x1 + 14, cy + 40 + i * 32), line, font=font_mono, fill=col)

def render_simulator(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)

    x1, y1, x2, y2 = rect

    # Simulator Header
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 90)], radius=10, fill=(17, 24, 39), outline=(124, 77, 255))
    draw.text((x1 + 16, y1 + 14), "Virtual GATT Peripheral Simulator", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Advertising Name: \"BLEPulse-Virtual-ESP32\" (Nordic ID 0x0059)", font=font_mono, fill=(124, 77, 255))
    draw.text((x1 + 16, y1 + 60), "Status: BROADCASTING ADVERTISING FRAMES (Connectable)", font=font_sub, fill=(0, 230, 118))

    # Profiles active
    sy = y1 + 104
    profiles = [
        ("Simulated Heart Rate Sensor (0x180D)", "Generates synthetic 60-100 BPM sinusoidal cardiac rhythm with Notify"),
        ("Simulated Environmental Station (0x181A)", "Streams virtual BME280 temperature (22.4°C) and humidity (48%) telemetry"),
        ("Emulated NUS UART Loopback Echo", "Replies to received write packets with uppercase ASCII echo response")
    ]
    for p_name, p_desc in profiles:
        draw.rounded_rectangle([(x1, sy), (x2, sy + 76)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
        draw.text((x1 + 16, sy + 12), p_name, font=font_bold, fill=(0, 229, 255))
        draw.text((x1 + 16, sy + 38), p_desc, font=font_sub, fill=(148, 163, 184))
        draw.rounded_rectangle([(x2 - 100, sy + 18), (x2 - 16, sy + 48)], radius=6, fill=(0, 40, 25), outline=(0, 230, 118))
        draw.text((x2 - 86, sy + 26), "ACTIVE", font=get_font(11, bold=True, mono=True), fill=(0, 230, 118))
        sy += 88

def render_terminal(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Serial Info
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 80)], radius=10, fill=(17, 24, 39), outline=(0, 229, 255))
    draw.text((x1 + 16, y1 + 14), "Bidirectional BLE Serial UART Terminal", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Channel: Nordic NUS (6E400001) | Mode: ASCII + Hex | Ending: CRLF", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 58), "Connected to: ESP32-S3 (24:0A:C4:88:99:AA)", font=font_sub, fill=(0, 230, 118))

    # Macro action pills
    my = y1 + 92
    macros = ["AT+GMR", "AT+BLEINIT=2", "0xAA 0x55 0x01", "PING", "RESET"]
    mx = x1
    for m in macros:
        draw.rounded_rectangle([(mx, my), (mx + 110, my + 32)], radius=6, fill=(30, 41, 59), outline=(51, 65, 85))
        draw.text((mx + 16, my + 8), m, font=font_mono, fill=(0, 229, 255))
        mx += 120

    # Terminal Monospace Console
    ty = my + 44
    draw.rounded_rectangle([(x1, ty), (x2, ty + 380)], radius=8, fill=(4, 6, 10), outline=(51, 65, 85))

    lines = [
        ("[09:41:10.012] [SYS] Auto-detected Nordic UART Service (NUS)", (100, 116, 139)),
        ("[09:41:10.050] [SYS] Subscribed to TX notifications (CCCD 0x2902 enabled)", (0, 230, 118)),
        ("[09:41:11.200] [TX ->] AT+GMR\\r\\n (8 bytes)", (0, 229, 255)),
        ("[09:41:11.240] [<- RX] AT version:2.4.0.0(s-4c3e8)\\r\\n", (0, 230, 118)),
        ("[09:41:11.250] [<- RX] SDK version:v5.1.2-dirty\\r\\n", (0, 230, 118)),
        ("[09:41:11.260] [<- RX] OK\\r\\n", (0, 230, 118)),
        ("[09:41:14.500] [TX ->] 0xAA 0x55 0x01 0x08 (Binary payload)", (0, 229, 255)),
        ("[09:41:14.530] [<- RX] 0xAA 0x55 0x01 0x08 0x00 0xFE (ACK response)", (0, 230, 118)),
        ("[09:41:18.100] [TX ->] PING\\r\\n", (0, 229, 255)),
        ("[09:41:18.125] [<- RX] PONG latency=25ms\\r\\n", (0, 230, 118))
    ]
    for i, (l_text, l_col) in enumerate(lines):
        draw.text((x1 + 14, ty + 16 + i * 35), l_text, font=font_mono, fill=l_col)

    # Send input box
    iy = ty + 392
    draw.rounded_rectangle([(x1, iy), (x2 - 100, iy + 42)], radius=6, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 14, iy + 12), "AT+WIFI_STATUS?", font=font_mono, fill=(248, 250, 252))
    draw.rounded_rectangle([(x2 - 90, iy), (x2, iy + 42)], radius=6, fill=(0, 229, 255))
    draw.text((x2 - 68, iy + 12), "SEND", font=font_badge, fill=(0, 0, 0))

def render_geiger(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Header
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 75)], radius=10, fill=(17, 24, 39), outline=(0, 230, 118))
    draw.text((x1 + 16, y1 + 14), "Audio & Haptic Geiger Counter Proximity Tracker", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Target: Nordic_HRM_Sensor (E0:4E:01:23:45:67)", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 56), "Audio Clicks: ON (Synthetic 1.5kHz PCM) | Haptic: ON", font=font_sub, fill=(0, 230, 118))

    # Geiger Meter Canvas
    gy = y1 + 90
    draw.rounded_rectangle([(x1, gy), (x2, gy + 320)], radius=12, fill=(4, 6, 10), outline=(51, 65, 85))

    # Arc gauge
    cx, cy_g = (x1 + x2) // 2, gy + 220
    draw.arc([(cx - 200, cy_g - 200), (cx + 200, cy_g + 200)], start=180, end=360, fill=(30, 41, 59), width=18)
    draw.arc([(cx - 200, cy_g - 200), (cx + 200, cy_g + 200)], start=180, end=310, fill=(0, 230, 118), width=18)

    # Needle pointing towards -48 dBm
    angle_rad = math.radians(310)
    nx = cx + int(170 * math.cos(angle_rad))
    ny = cy_g + int(170 * math.sin(angle_rad))
    draw.line([(cx, cy_g), (nx, ny)], fill=(255, 61, 0), width=4)
    draw.ellipse([(cx - 14, cy_g - 14), (cx + 14, cy_g + 14)], fill=(248, 250, 252))

    # Gauge labels
    draw.text((cx - 190, cy_g + 10), "-100 dBm", font=font_mono, fill=(100, 116, 139))
    draw.text((cx + 130, cy_g + 10), "-35 dBm", font=font_mono, fill=(0, 230, 118))

    # Prominent RSSI & Distance
    draw.text((cx - 100, gy + 80), "-48 dBm", font=get_font(42, bold=True, mono=True), fill=(0, 230, 118))
    draw.text((cx - 80, gy + 140), "Estimated Distance: ~0.42 meters", font=font_bold, fill=(248, 250, 252))

    # Proximity zone badge
    draw.rounded_rectangle([(cx - 110, gy + 175), (cx + 110, gy + 205)], radius=6, fill=(0, 40, 25), outline=(0, 230, 118))
    draw.text((cx - 86, gy + 182), "ZONE: IMMEDIATE (<0.5m)", font=font_badge, fill=(0, 230, 118))

    # Audio/Haptic metrics
    my = gy + 334
    draw.rounded_rectangle([(x1, my), (x2, my + 110)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, my + 14), "PULSE ENGINE TELEMETRY", font=font_bold, fill=(0, 229, 255))
    draw.text((x1 + 16, my + 40), "Click Frequency: 18.4 Hz (High Proximity Rate)", font=font_mono, fill=(248, 250, 252))
    draw.text((x1 + 16, my + 62), "Haptic Vibration Waveform: Periodic 15ms pulses @ 54ms intervals", font=font_mono, fill=(148, 163, 184))
    draw.text((x1 + 16, my + 84), "EMA Smoothed Filter: Alpha 0.25 (Stabilized RSSI)", font=font_mono, fill=(100, 116, 139))

def render_audit(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Chipset Fingerprint Banner
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 110)], radius=10, fill=(17, 24, 39), outline=(0, 229, 255))
    draw.text((x1 + 16, y1 + 14), "CHIPSET FINGERPRINT IDENTIFIED", font=get_font(11, bold=True), fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 34), "Nordic Semiconductor nRF52840", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 58), "Architecture: ARM Cortex-M4F with FPU | Confidence: 95%", font=font_sub, fill=(0, 230, 118))
    draw.text((x1 + 16, y1 + 80), "Identified via Nordic UART Profile & SIG Company ID 0x0059", font=font_mono, fill=(148, 163, 184))

    # Security Score Card
    sy = y1 + 124
    draw.rounded_rectangle([(x1, sy), (x2, sy + 75)], radius=8, fill=(17, 24, 39), outline=(255, 179, 0))
    draw.text((x1 + 16, sy + 14), "SECURITY AUDIT SCORE", font=get_font(11, bold=True), fill=(255, 179, 0))
    draw.text((x1 + 16, sy + 38), "75 / 100", font=get_font(24, bold=True, mono=True), fill=(255, 179, 0))
    draw.text((x1 + 160, sy + 44), "MEDIUM VULNERABILITY EXPOSURE (2 Warnings)", font=font_sub, fill=(248, 250, 252))

    # Findings
    fy = sy + 90
    findings = [
        ("HIGH", "Unencrypted Serial REPL / UART Console Exposed", "Nordic UART RX accepts raw writes without requiring BLE SC bonding or encryption.", (255, 61, 0)),
        ("MEDIUM", "Write Without Response Buffer Saturation Risk", "Characteristics accept unacknowledged bursts, vulnerable to MCU packet drop.", (255, 179, 0)),
        ("INFO", "Nordic SoftDevice SweynTooth Advisory", "Firmware version S140 v7.0.1 - ensure patched against Key Size Overflow.", (0, 229, 255))
    ]
    for sev, title, desc, col in findings:
        draw.rounded_rectangle([(x1, fy), (x2, fy + 88)], radius=8, fill=(17, 24, 39), outline=col)
        draw.rounded_rectangle([(x1 + 14, fy + 12), (x1 + 80, fy + 34)], radius=4, fill=col)
        draw.text((x1 + 22, fy + 16), sev, font=font_badge, fill=(0, 0, 0))
        draw.text((x1 + 90, fy + 14), title, font=font_bold, fill=(248, 250, 252))
        draw.text((x1 + 14, fy + 44), desc, font=font_sub, fill=(148, 163, 184))
        fy += 100

def render_macro(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Macro Header
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 85)], radius=10, fill=(17, 24, 39), outline=(0, 229, 255))
    draw.text((x1 + 16, y1 + 14), "GATT Macro Automation & Sequence Test Runner", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Scenario: Nordic NUS Echo & Reliability Test Suite", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, y1 + 58), "Target: Nordic_HRM_Sensor | Result: 5 / 5 PASSED (100%)", font=font_sub, fill=(0, 230, 118))

    # Steps list
    my = y1 + 100
    steps = [
        ("Step 1: Request MTU 247", "PASSED", "Negotiated 247 bytes in 28ms", (0, 230, 118)),
        ("Step 2: Request High-Speed LE 2M PHY", "PASSED", "Switched TX/RX to 2M in 42ms", (0, 230, 118)),
        ("Step 3: Enable NUS TX CCCD Notification", "PASSED", "Subscribed to handle 0x0014 in 35ms", (0, 230, 118)),
        ("Step 4: Transmit Loopback String 'PING'", "PASSED", "Sent 4 bytes without response in 12ms", (0, 230, 118)),
        ("Step 5: Assert RX Echo Matches 'PING'", "PASSED", "Received expected ASCII response in 22ms", (0, 230, 118))
    ]
    for s_name, status, details, col in steps:
        draw.rounded_rectangle([(x1, my), (x2, my + 68)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
        draw.text((x1 + 16, my + 12), s_name, font=font_bold, fill=(248, 250, 252))
        draw.rounded_rectangle([(x2 - 90, my + 10), (x2 - 14, my + 34)], radius=4, fill=(0, 40, 25), outline=col)
        draw.text((x2 - 78, my + 16), status, font=font_badge, fill=col)
        draw.text((x1 + 16, my + 38), details, font=font_mono, fill=(148, 163, 184))
        my += 80

def render_fota(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)
    font_badge = get_font(11, bold=True, mono=True)

    x1, y1, x2, y2 = rect

    # Top Header
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 80)], radius=10, fill=(17, 24, 39), outline=(124, 77, 255))
    draw.text((x1 + 16, y1 + 14), "FOTA Firmware Flasher", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, y1 + 38), "Target Protocol: ESP32 BLE OTA (NimBLE / ESP-IDF)", font=font_mono, fill=(124, 77, 255))
    draw.text((x1 + 16, y1 + 58), "Target Device: ESP32-S3 Environment Station", font=font_sub, fill=(0, 230, 118))

    # Firmware Metadata Card
    my = y1 + 94
    draw.rounded_rectangle([(x1, my), (x2, my + 100)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 16, my + 14), "firmware_esp32_v2.1.0.bin (64 KB / 65,536 bytes)", font=font_bold, fill=(248, 250, 252))
    draw.text((x1 + 16, my + 38), "Header Magic: 0xE9 0x03 0x02 0x20 | Format: ESP32 App Binary", font=font_mono, fill=(0, 229, 255))
    draw.text((x1 + 16, my + 60), "CRC32: 0x9E4B21D0 | SHA-256: 7f83b1657ff1... (Verified)", font=font_mono, fill=(0, 230, 118))
    draw.text((x1 + 16, my + 80), "Chunk Size: 244 bytes | Inter-Chunk Delay: 5ms", font=font_mono, fill=(148, 163, 184))

    # Progress Card
    py_c = my + 114
    draw.rounded_rectangle([(x1, py_c), (x2, py_c + 120)], radius=10, fill=(17, 24, 39), outline=(0, 229, 255))
    draw.text((x1 + 16, py_c + 14), "FLASHING IN PROGRESS...", font=get_font(12, bold=True), fill=(0, 229, 255))
    draw.text((x2 - 70, py_c + 14), "68%", font=get_font(18, bold=True, mono=True), fill=(0, 229, 255))

    # Progress bar
    draw.rounded_rectangle([(x1 + 16, py_c + 44), (x2 - 16, py_c + 56)], radius=6, fill=(30, 41, 59))
    draw.rounded_rectangle([(x1 + 16, py_c + 44), (x1 + int((x2 - x1 - 32) * 0.68), py_c + 56)], radius=6, fill=(0, 229, 255))

    draw.text((x1 + 16, py_c + 72), "Throughput: 18.4 kbps (~2.3 kB/s)", font=font_mono, fill=(0, 230, 118))
    draw.text((x1 + 16, py_c + 94), "Transferred: 44,564 / 65,536 B (Chunk 182 / 269) | ETA: 9s", font=font_mono, fill=(148, 163, 184))

    # Console Logs
    ly = py_c + 134
    draw.rounded_rectangle([(x1, ly), (x2, ly + 140)], radius=8, fill=(4, 6, 10), outline=(51, 65, 85))
    fota_logs = [
        "[09:41:30.010] [TX] OTA_BEGIN: size 65536 bytes -> ACK",
        "[09:41:30.120] [TX] Writing chunk 100/269 (244 bytes)",
        "[09:41:30.550] [TX] Writing chunk 150/269 (244 bytes)",
        "[09:41:30.880] [TX] Writing chunk 182/269 (244 bytes) - OK"
    ]
    for i, flog in enumerate(fota_logs):
        draw.text((x1 + 14, ly + 14 + i * 30), flog, font=font_mono, fill=(148, 163, 184))

def render_fft(draw, rect, px, py, pw, ph):
    font_bold = get_font(15, bold=True)
    font_mono = get_font(12, mono=True)
    font_sub = get_font(13)

    x1, y1, x2, y2 = rect

    # Top Controls
    draw.rounded_rectangle([(x1, y1), (x2, y1 + 54)], radius=8, fill=(17, 24, 39), outline=(51, 65, 85))
    draw.text((x1 + 14, y1 + 16), "Mode: Frequency Spectrum (FFT) | Window: Hanning", font=font_bold, fill=(248, 250, 252))

    draw.rounded_rectangle([(x2 - 130, y1 + 10), (x2 - 12, y1 + 44)], radius=6, fill=(124, 77, 255))
    draw.text((x2 - 116, y1 + 18), "FFT SPECTRUM", font=get_font(11, bold=True, mono=True), fill=(255, 255, 255))

    # FFT Canvas
    scope_y1 = y1 + 64
    scope_y2 = scope_y1 + 420
    draw.rounded_rectangle([(x1, scope_y1), (x2, scope_y2)], radius=12, fill=(4, 6, 10), outline=(51, 65, 85))

    # Grid
    for i in range(1, 8):
        gx = x1 + (x2 - x1) * i // 8
        draw.line([(gx, scope_y1), (gx, scope_y2)], fill=(22, 32, 50), width=1)
    for j in range(1, 6):
        gy = scope_y1 + (scope_y2 - scope_y1) * j // 6
        draw.line([(x1, gy), (x2, gy)], fill=(22, 32, 50), width=1)

    # Spectrum gradient bars
    num_bins = 48
    bar_w = (x2 - x1 - 20) // num_bins
    bx = x1 + 10

    for b in range(num_bins):
        freq = b * (25.0 / num_bins)
        # Synthesize peak at 1.2 Hz (cardiac rhythm) and harmonic at 2.4 Hz
        if abs(freq - 1.2) < 0.6:
            norm_mag = 0.92 - abs(freq - 1.2) * 0.7
        elif abs(freq - 2.4) < 0.6:
            norm_mag = 0.45 - abs(freq - 2.4) * 0.4
        elif abs(freq - 10.0) < 1.0:
            norm_mag = 0.25
        else:
            norm_mag = 0.05 + (b % 5) * 0.02

        bar_h = int(norm_mag * 340)
        by = scope_y2 - bar_h - 10

        draw.rectangle([(bx, by), (bx + bar_w - 2, scope_y2 - 10)], fill=(124, 77, 255))
        draw.rectangle([(bx, by), (bx + bar_w - 2, by + 10)], fill=(0, 229, 255))
        bx += bar_w

    # Peak frequency marker
    peak_x = x1 + 10 + int(1.2 * (num_bins / 25.0) * bar_w)
    draw.line([(peak_x, scope_y1 + 40), (peak_x, scope_y2 - 10)], fill=(0, 230, 118), width=2)
    draw.ellipse([(peak_x - 6, scope_y1 + 34), (peak_x + 6, scope_y1 + 46)], fill=(0, 230, 118))
    draw.text((peak_x + 10, scope_y1 + 30), "Peak: 1.23 Hz (74 BPM)", font=font_mono, fill=(0, 230, 118))

    # Telemetry metrics row
    my = scope_y2 + 16
    cards = [
        {"name": "Dominant Peak", "val": "1.23 Hz", "sub": "74.0 BPM Heart Rate", "col": (0, 230, 118)},
        {"name": "Nyquist Limit", "val": "25.0 Hz", "sub": "Sampling Rate: 50.0 Hz", "col": (0, 229, 255)},
        {"name": "FFT Resolution", "val": "256 Bins", "sub": "Window: Hanning (Cooley-Tukey)", "col": (124, 77, 255)}
    ]
    cw = (x2 - x1 - 20) // 3
    for i, c in enumerate(cards):
        cx = x1 + i * (cw + 10)
        draw.rounded_rectangle([(cx, my), (cx + cw, my + 90)], radius=8, fill=(17, 24, 39), outline=c["col"])
        draw.text((cx + 12, my + 10), c["name"], font=font_sub, fill=c["col"])
        draw.text((cx + 12, my + 34), c["val"], font=get_font(18, bold=True, mono=True), fill=(248, 250, 252))
        draw.text((cx + 12, my + 64), c["sub"], font=font_mono, fill=(148, 163, 184))

def main():
    print("Generating high-fidelity UI screenshots for all modules...")

    s1 = draw_device_mockup(1200, 1650, "BLEPulse - Real-Time Advertising Scanner", "Scans BLE 5.x broadcast channels, decodes iBeacon & Eddystone, and exports Wireshark PCAPs", render_scanner)
    s1.save(os.path.join(IMG_DIR, "blepulse_scanner.png"))
    print("[OK] blepulse_scanner.png")

    s2 = draw_device_mockup(1200, 1650, "BLEPulse - Interactive GATT Explorer", "Service and characteristic browser with MTU negotiation, PHY switching, and type decoders", render_explorer)
    s2.save(os.path.join(IMG_DIR, "blepulse_explorer.png"))
    print("[OK] blepulse_explorer.png")

    s3 = draw_device_mockup(1200, 1650, "BLEPulse - Live Waveform Oscilloscope", "Hardware-accelerated 60fps real-time waveform plotter for Notify & Indicate telemetry streams", render_oscilloscope)
    s3.save(os.path.join(IMG_DIR, "blepulse_oscilloscope.png"))
    print("[OK] blepulse_oscilloscope.png")

    s_fft = draw_device_mockup(1200, 1650, "BLEPulse - Real-Time FFT Spectrum Analyzer", "Radix-2 Cooley-Tukey FFT spectral decomposition with Hanning windowing and peak tracking", render_fft)
    s_fft.save(os.path.join(IMG_DIR, "blepulse_fft.png"))
    print("[OK] blepulse_fft.png")

    s_term = draw_device_mockup(1200, 1650, "BLEPulse - Bidirectional Serial UART Terminal", "Full-duplex serial console for Nordic NUS, ESP32 SPP, and custom embedded REPLs", render_terminal)
    s_term.save(os.path.join(IMG_DIR, "blepulse_terminal.png"))
    print("[OK] blepulse_terminal.png")

    s_geiger = draw_device_mockup(1200, 1650, "BLEPulse - Audio & Haptic Geiger Tracker", "Analog needle meter with dynamic audio clicking and synchronized tactile pulses for signal hunting", render_geiger)
    s_geiger.save(os.path.join(IMG_DIR, "blepulse_geiger.png"))
    print("[OK] blepulse_geiger.png")

    s_audit = draw_device_mockup(1200, 1650, "BLEPulse - Chipset Fingerprinting & Security Audit", "Heuristic identification of Nordic/ESP32/TI SoCs and vulnerability scoring for IoT peripherals", render_audit)
    s_audit.save(os.path.join(IMG_DIR, "blepulse_audit.png"))
    print("[OK] blepulse_audit.png")

    s_macro = draw_device_mockup(1200, 1650, "BLEPulse - GATT Macro Automation Runner", "Automated sequence execution engine for hardware validation, commissioning, and regression tests", render_macro)
    s_macro.save(os.path.join(IMG_DIR, "blepulse_macro.png"))
    print("[OK] blepulse_macro.png")

    s_fota = draw_device_mockup(1200, 1650, "BLEPulse - Nordic DFU & ESP32 FOTA Flasher", "Over-the-air firmware flashing engine with CRC32 verification and throughput telemetry", render_fota)
    s_fota.save(os.path.join(IMG_DIR, "blepulse_fota.png"))
    print("[OK] blepulse_fota.png")

    s4 = draw_device_mockup(1200, 1650, "BLEPulse - IoT Peripheral Fuzzer", "Stress-tests microcontrollers and GATT servers with boundary values, mutations, and overflow probes", render_fuzzer)
    s4.save(os.path.join(IMG_DIR, "blepulse_fuzzer.png"))
    print("[OK] blepulse_fuzzer.png")

    s5 = draw_device_mockup(1200, 1650, "BLEPulse - Virtual Peripheral Simulator", "Turns Android phone into an emulated GATT peripheral server to test companion apps and firmware", render_simulator)
    s5.save(os.path.join(IMG_DIR, "blepulse_simulator.png"))
    print("[OK] blepulse_simulator.png")

    # Generate Animated Demo GIF using FFmpeg
    print("Generating comprehensive animated demo GIF (blepulse_demo.gif)...")
    frames = [s1, s2, s3, s_fft, s_term, s_geiger, s_audit, s_macro, s_fota, s4, s5]
    frame_paths = []
    for i, f in enumerate(frames):
        p = os.path.join(IMG_DIR, f"temp_frame_{i}.png")
        f.resize((800, 1100), Image.Resampling.LANCZOS).save(p)
        frame_paths.append(p)

    gif_path = os.path.join(IMG_DIR, "blepulse_demo.gif")
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
    ldraw.rounded_rectangle([(24, 24), (488, 488)], radius=110, fill=(11, 15, 25), outline=(0, 229, 255), width=8)

    wave = [
        (80, 256), (160, 256), (190, 210), (220, 310), (256, 120),
        (292, 380), (322, 230), (352, 280), (380, 256), (432, 256)
    ]
    ldraw.line(wave, fill=(0, 229, 255), width=10)

    ldraw.line([(256, 140), (256, 372)], fill=(255, 255, 255), width=12)
    ldraw.line([(200, 196), (312, 316)], fill=(255, 255, 255), width=12)
    ldraw.line([(200, 316), (312, 196)], fill=(255, 255, 255), width=12)
    ldraw.line([(312, 196), (256, 140)], fill=(255, 255, 255), width=12)
    ldraw.line([(312, 316), (256, 372)], fill=(255, 255, 255), width=12)

    logo.save(os.path.join(IMG_DIR, "blepulse_logo.png"))
    print("[OK] blepulse_logo.png created for README header")

if __name__ == "__main__":
    main()

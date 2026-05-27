# é¨ç½²æå

## 1. æå¡å¨é¨ç½²

```bash
# ä¸ä¼  server.py å requirements.txt å°æå¡å¨
scp server.py requirements.txt root@ä½ çæå¡å¨IP:/opt/wulong/

# SSH ç»å½
ssh root@ä½ çæå¡å¨IP

# å®è£ä¾èµ
pip3 install -r /opt/wulong/requirements.txt

# çæéæºå¯é¥ï¼è®°ä¸æ¥ï¼åé¢è¦ç¨ï¼
export WULONG_SECRET=$(python3 -c "import secrets; print(secrets.token_hex(16))")
export WULONG_ADMIN=$(python3 -c "import secrets; print(secrets.token_hex(8))")

# è®°ä¸è¿ä¸¤ä¸ªå¼ï¼éå° admin.py å systemd é
echo "WULONG_SECRET=$WULONG_SECRET"
echo "WULONG_ADMIN=$WULONG_ADMIN"

# ç¨ systemd å®æ¤è¿ç¨
sudo tee /etc/systemd/system/wulong.service << 'UNIT'
[Unit]
Description=Wulong Dictionary Activation
After=network.target

[Service]
Type=simple
User=nobody
WorkingDirectory=/opt/wulong
Environment=WULONG_SECRET=æ¿æ¢ä¸ºä½ çå¯é¥
Environment=WULONG_ADMIN=æ¿æ¢ä¸ºä½ çadminå¯é¥
Environment=PORT=8700
ExecStart=/usr/bin/python3 /opt/wulong/server.py
Restart=on-failure

[Install]
WantedBy=multi-user.target
UNIT

sudo systemctl daemon-reload
sudo systemctl enable --now wulong.service
sudo systemctl status wulong.service
```

## 2. Nginx ååä»£çï¼åªæ´é²ä¸ä¸ªè·¯å¾ï¼

```nginx
# å¨ç°æ nginx éç½®éå ï¼
location /wulong/activate {
    proxy_pass http://127.0.0.1:8700/activate;
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
}
location /wulong/health {
    proxy_pass http://127.0.0.1:8700/health;
    proxy_set_header Host $host;
}
# migrate ä¸å¯¹å¤æ´é²ï¼åªå¨åç½æéè¿ VPN è®¿é®
```

è¿æ ·å¤é¨åªçå°ä¸ä¸ª `/wulong/activate` è·¯å¾ï¼æ»å»é¢æå°ã
æå¡å¨ä¸ 8700 ç«¯å£åªçå¬ 127.0.0.1ï¼ä¸å¯¹å¤ã

## 3. çææ¬å°æ¿æ´»ç 

å¨ä½ èªå·±ççµèä¸ï¼ä¸æ¯æå¡å¨ï¼ï¼
```bash
export WULONG_SECRET="åä¸é£ä¸ªå¯é¥"
python3 admin.py generate 0023 æç«è¶
# ä¼è¾åºæ¿æ´»ç ï¼å¡«å°éè¯·å½é
```

## 4. æµè¯

```bash
# å¨æå¡å¨ä¸èªæµ
curl -X POST http://127.0.0.1:8700/activate \
  -H "Content-Type: application/json" \
  -d '{"invite_no":"0023","name":"æç«è¶","code":"çæçæ¿æ´»ç ","device_id":"test123"}'

# åºè¿å {"ok": true, "msg": "æ¿æ´»æå"}
```

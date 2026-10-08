# -*- coding: utf-8 -*-
"""Database MessageSource bridge: reads WeChat local DB via wechatauto, emits JSON lines.

Modes:
  history <wxid> [limit]   -> print one JSON array line of recent messages
  listen  <wxid>           -> print one JSON line per new message (blocking)

Usage:
  python db_bridge.py history wxid_oate4co6z2z329 20
  python db_bridge.py listen  wxid_oate4co6z2z329
"""
import sys, os, json, time
try:
    sys.stdout.reconfigure(encoding='utf-8')
except Exception:
    pass

# Ensure wechatauto is importable (we run from wechatapi-main dir)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from wechatauto.db import WeChatDB, Listener


def msg_to_dict(m, self_wxid="", contact_wxid="", sender_index=None):
    sid = m.get("sender_id")
    su = m.get("sender_username") or ""
    # Resolve ME/OTHER:
    # 1. If sender_index maps this sender_id to self_wxid -> ME
    # 2. If sender_index maps this sender_id to contact_wxid -> OTHER
    # 3. If sender_id == 1 (WeChat special self row in message table) -> ME
    # 4. Otherwise -> OTHER (conservative)
    is_self = False
    if sender_index and sid is not None:
        try:
            resolved = sender_index.get(int(sid), "")
        except (TypeError, ValueError):
            resolved = ""
        if resolved == self_wxid:
            is_self = True
        elif resolved:
            su = resolved  # use the wxid from index
    # WeChat message table: real_sender_id=1 is always the account owner (self)
    if sid == 1 or sid == "1":
        is_self = True
        if not su:
            su = self_wxid
    return {
        "localId": m.get("local_id"),
        "sortSeq": m.get("sort_seq"),
        "senderId": sid,
        "senderUsername": su,
        "isSelf": is_self,
        "createTime": m.get("create_time"),
        "type": m.get("type"),
        "content": m.get("content") or "",
    }


def main():
    if len(sys.argv) < 2:
        print("usage: db_bridge.py <detect|history|listen> ...", file=sys.stderr)
        sys.exit(2)
    mode = sys.argv[1]

    if mode == "detect":
        # Environment detection: list account directories on disk (no key extraction needed)
        import traceback
        try:
            from wechatauto.db import list_accounts, auto_detect_db_dir
            db_dir = auto_detect_db_dir()
            print(f"[DbDetect] db_dir = {db_dir}", file=sys.stderr)
            accounts = list_accounts(db_dir)
            print(f"[DbDetect] accounts found = {len(accounts)}", file=sys.stderr)
            result = {"ok": True, "dbDir": db_dir or "", "accounts": []}
            for a in accounts:
                entry = {
                    "account": a["account"],
                    "wxid": a["wxid"],
                    "lastActivity": a["last_activity"],
                    "nickname": ""
                }
                result["accounts"].append(entry)
            print(f"[DbDetect] result = {len(result['accounts'])} accounts", file=sys.stderr)
            print(json.dumps(result, ensure_ascii=False))
            sys.exit(0)
        except Exception as e:
            traceback.print_exc(file=sys.stderr)
            print(json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False))
            sys.exit(1)

    if mode == "selfinfo":
        import traceback
        try:
            acct = sys.argv[2] if len(sys.argv) > 2 else None
            print(f"[DbSelfInfo] account={acct}", file=sys.stderr)
            db = WeChatDB(account=acct) if acct else WeChatDB()
            info = db.get_self_info() or {}
            print(f"[DbSelfInfo] info keys={list(info.keys())}", file=sys.stderr)
            out = {"ok": True,
                   "wxid": info.get("username", ""),
                   "nickname": info.get("nick_name", "") or info.get("nickname", ""),
                   "wechatId": info.get("wechat_id", "") or info.get("alias", "") or info.get("account", "")}
            print(json.dumps(out, ensure_ascii=False))
            sys.exit(0)
        except Exception as e:
            traceback.print_exc(file=sys.stderr)
            print(json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False))
            sys.exit(1)

    if mode == "contacts":
        import traceback
        try:
            acct = sys.argv[2] if len(sys.argv) > 2 else None
            print(f"[DbContacts] account={acct}", file=sys.stderr)
            db = WeChatDB(account=acct) if acct else WeChatDB()
            sessions = db.get_sessions(limit=200) or []
            print(f"[DbContacts] sessions={len(sessions)}", file=sys.stderr)
            # Build username -> (nick_name, remark) lookup from contact.db
            nick_map = {}
            try:
                for rel, path, _ in db._db_files:
                    if os.path.basename(path) != "contact.db":
                        continue
                    conn = db._open(rel)
                    try:
                        rows = conn.execute("SELECT username, nick_name, remark FROM contact").fetchall()
                        for r in rows:
                            nick_map[r["username"]] = (r["nick_name"] or "", r["remark"] or "")
                    finally:
                        conn.close()
                    break
            except Exception as e:
                print(f"[DbContacts] contact lookup failed: {e}", file=sys.stderr)
            out = {"ok": True, "contacts": []}
            for s in sessions:
                wxid = s.get("username") or ""
                if not wxid or wxid.endswith("@chatroom") or wxid in ("fmessage", "brandsessionholder", "brandservicesessionholder", "notifymessage", "@placeholder_foldgroup"):
                    continue
                nick, remark = nick_map.get(wxid, ("", ""))
                out["contacts"].append({
                    "wxid": wxid,
                    "nickname": nick,
                    "remark": remark,
                    "lastTime": s.get("last_time") or 0,
                })
            print(f"[DbContacts] returned={len(out['contacts'])}", file=sys.stderr)
            print(json.dumps(out, ensure_ascii=False))
            sys.exit(0)
        except Exception as e:
            traceback.print_exc(file=sys.stderr)
            print(json.dumps({"ok": False, "error": str(e)}, ensure_ascii=False))
            sys.exit(1)

    if len(sys.argv) < 3:
        print("usage: db_bridge.py <history|listen> <wxid> [limit]", file=sys.stderr)
        sys.exit(2)
    wxid = sys.argv[2]
    limit = int(sys.argv[3]) if len(sys.argv) > 3 else 20

    # Auto-detect account (we don't override unless env WECHAT_ACCOUNT is set)
    kwargs = {}
    acct = os.environ.get("WECHAT_ACCOUNT")
    if acct:
        kwargs["account"] = acct
    db = WeChatDB(**kwargs)

    # Resolve self wxid and sender index for ME/OTHER determination
    self_wxid = ""
    sender_index = {}
    try:
        info = db.get_self_info()
        self_wxid = info.get("username", "")
    except Exception:
        pass
    try:
        sender_index = db._sender_id_index()
    except Exception:
        pass

    if mode == "history":
        msgs = db.get_messages(wxid, limit=limit)
        out = [msg_to_dict(m, self_wxid, wxid, sender_index) for m in reversed(msgs)]
        print(json.dumps(out, ensure_ascii=False))
        sys.exit(0)

    if mode == "listen":
        def on_msg(msg, lst):
            d = msg_to_dict(msg, self_wxid, wxid, sender_index)
            d["event"] = "new"
            print(json.dumps(d, ensure_ascii=False), flush=True)

        listener = Listener(db, interval=1.0)
        listener.add_listener(wxid, on_msg)
        listener.start()
        print(json.dumps({"event": "ready", "wxid": wxid}, ensure_ascii=False), flush=True)
        while True:
            time.sleep(1)
    else:
        print(f"unknown mode: {mode}", file=sys.stderr)
        sys.exit(2)


if __name__ == "__main__":
    main()

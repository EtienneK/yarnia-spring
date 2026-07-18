import { useEffect, useRef, useState } from "react";
import type { ChatMessage } from "./types.ts";

export const MAX_CHAT_LENGTH = 300;

/**
 * Slide-in party chat. Takes no layout space: a floating button (with unread
 * badge) opens a right-hand drawer over the game.
 */
export function Chat({
  messages,
  unread,
  open,
  onToggle,
  onSend,
  myPlayerId,
}: {
  messages: ChatMessage[];
  unread: number;
  open: boolean;
  onToggle: () => void;
  onSend: (text: string) => void;
  myPlayerId: string;
}) {
  const [draft, setDraft] = useState("");
  const bottomRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    if (open) {
      bottomRef.current?.scrollIntoView({ block: "nearest" });
      inputRef.current?.focus();
    }
  }, [open, messages.length]);

  const send = (e: React.FormEvent) => {
    e.preventDefault();
    const text = draft.trim();
    if (!text) return;
    onSend(text);
    setDraft("");
  };

  return (
    <>
      <button
        className="btn btn-circle btn-primary fixed bottom-4 right-4 z-40 shadow-lg text-xl"
        onClick={onToggle}
        title={open ? "Close chat" : "Open chat"}
      >
        💬
        {unread > 0 && !open && (
          <span className="badge badge-error badge-sm absolute -top-1 -right-1 anim-pop-in">
            {unread > 9 ? "9+" : unread}
          </span>
        )}
      </button>

      <div
        className={`fixed inset-y-0 right-0 z-50 w-80 max-w-[85vw] bg-base-100 shadow-2xl flex flex-col transition-transform duration-300 ${
          open ? "translate-x-0" : "translate-x-full"
        }`}
      >
        <div className="flex items-center justify-between p-3 border-b border-base-300">
          <span className="font-bold">Party Chat</span>
          <button className="btn btn-ghost btn-sm" onClick={onToggle}>
            ✕
          </button>
        </div>

        <div className="flex-1 overflow-y-auto p-3 text-left">
          {messages.length === 0 && (
            <p className="text-gray-500 text-sm">
              Say hi! Everyone in the party can read this.
            </p>
          )}
          {messages.map((m) => (
            <div key={m.id} className="mb-2 text-sm break-words">
              <span
                className="font-bold"
                style={m.senderColor ? { color: m.senderColor } : undefined}
              >
                {m.senderName}
                {m.senderId === myPlayerId ? " (you)" : ""}
                {m.bot ? " 🤖" : ""}
              </span>{" "}
              <span>{m.text}</span>
            </div>
          ))}
          <div ref={bottomRef} />
        </div>

        <form onSubmit={send} className="p-3 border-t border-base-300 flex gap-2">
          <input
            ref={inputRef}
            type="text"
            className="input input-sm flex-1"
            placeholder="Message..."
            value={draft}
            maxLength={MAX_CHAT_LENGTH}
            onChange={(e) => setDraft(e.target.value)}
          />
          <button type="submit" className="btn btn-sm btn-primary" disabled={!draft.trim()}>
            Send
          </button>
        </form>
      </div>
    </>
  );
}

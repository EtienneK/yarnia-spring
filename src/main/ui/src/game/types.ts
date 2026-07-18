export type PartyPhase = "WAITING" | "PLAYING" | "FINISHED";

export interface PartySnapshot {
  partyPhase: PartyPhase;
  members: Record<
    string,
    {
      name: string;
      color: string;
      host: boolean;
      ready: boolean;
      connected: boolean;
      bot: boolean;
    }
  >;
}

export type GamePhase = "SUBMITTING" | "VOTING" | "REVEAL" | "FINISHED";

export interface StoryLine {
  text: string;
  authorId: string | null;
  authorName: string | null;
  color: string | null;
  moral: boolean;
}

export interface SubmissionView {
  id: string;
  text: string;
  authorId: string | null;
  authorName: string | null;
  color: string | null;
  votes: number;
}

export interface GameSnapshot {
  round: number;
  totalRounds: number;
  phase: GamePhase;
  phaseEndsAt: number;
  maxSubmissionLength: number;
  prompt: string;
  story: StoryLine[];
  submissions: SubmissionView[] | null;
  submitted: string[];
  voted: string[];
  scores: Record<string, number>;
  roundPoints: Record<string, number> | null;
  winnerSubmissionId: string | null;
  winnerIds: string[] | null;
}

export interface Publish {
  <T>(msg: { destination: string; body: T }): void;
}

export interface ChatMessage {
  id: string;
  senderId: string;
  senderName: string;
  senderColor: string | null;
  bot: boolean;
  text: string;
  at: number;
}

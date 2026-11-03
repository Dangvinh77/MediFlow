import { publicApi } from "@/lib/api";
import type { PageResult } from "@/lib/types";
import type { AccountRole } from "./types";

export interface MailboxAccount { accountId: string; role: AccountRole; displayName: string; email: string }
export interface MailboxMessage { messageId: string; subject: string; from: string; to: string; createdAt: string; preview: string }
export interface MailboxDetail extends Omit<MailboxMessage, "preview"> { textBody: string; htmlBody: string }
export const accountSecurityApi = {
  forgot: (email: string) => publicApi.post<unknown>("/v1/auth/forgot-password", { email }),
  reset: (token: string, newPassword: string) => publicApi.post<unknown>("/v1/auth/reset-password", { token, newPassword }),
  demoConfig: () => publicApi.get<{ enabled: boolean }>("/v1/demo/mailbox/config"),
  demoAccounts: (role: string, keyword: string, page = 0) => {
    const query = new URLSearchParams({ page: String(page), size: "20" });
    if (role) query.set("role", role);
    if (keyword.trim()) query.set("keyword", keyword.trim());
    return publicApi.get<PageResult<MailboxAccount>>(`/v1/demo/mailbox/accounts?${query}`);
  },
  demoMessages: (id: string, page = 0) => publicApi.get<PageResult<MailboxMessage>>(`/v1/demo/mailbox/accounts/${encodeURIComponent(id)}/messages?page=${page}&size=20`),
  demoMessage: (id: string, messageId: string) => publicApi.get<MailboxDetail>(`/v1/demo/mailbox/accounts/${encodeURIComponent(id)}/messages/${encodeURIComponent(messageId)}`),
};

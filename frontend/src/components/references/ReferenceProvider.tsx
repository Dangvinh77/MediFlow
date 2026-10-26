"use client";

import { createContext, useContext, type ReactNode } from "react";

export interface ReferenceOption { id: string; label: string }
export interface ReferencePage { items: ReferenceOption[]; hasMore: boolean; hint?: string }
export interface ReferenceDirectory {
  canResolve?: (kind: string) => boolean;
  canSearch: (kind: string) => boolean;
  search: (kind: string, query: string, page: number, scope?: string) => Promise<ReferencePage>;
  resolve: (kind: string, id: string) => Promise<string | null>;
}

const DirectoryContext = createContext<ReferenceDirectory | null>(null);
export const useReferenceDirectory = () => useContext(DirectoryContext);
const identities = new WeakMap<ReferenceDirectory, number>();
let nextIdentity = 0;

export function ReferenceProvider({ directory, children }: { directory: ReferenceDirectory; children: ReactNode }) {
  if (!identities.has(directory)) identities.set(directory, ++nextIdentity);
  return <DirectoryContext.Provider key={identities.get(directory)} value={directory}>{children}</DirectoryContext.Provider>;
}

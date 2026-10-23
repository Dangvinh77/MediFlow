"use client";
import { useCallback, useEffect, useRef } from "react";

const GUARD = "__mediflowDraftGuard";

/** A same-URL history entry catches browser traversal before Next can discard the draft. */
export function useUnsavedChanges() {
  const dirty = useRef(false);
  const arm = useRef<() => void>(() => {});
  const markDirty = useCallback(() => { dirty.current = true; arm.current(); }, []);
  const markSaved = useCallback(() => { dirty.current = false; }, []);
  const confirmLeave = useCallback(() => {
    if (!dirty.current) return true;
    if (!window.confirm("Bạn có dữ liệu chưa lưu. Rời trang và bỏ các thay đổi?")) return false;
    dirty.current = false;
    return true;
  }, []);
  useEffect(() => {
    const push = history.pushState;
    const replace = history.replaceState;
    let armed = Boolean(history.state?.[GUARD]);
    let restoring = false;
    arm.current = () => {
      if (armed) return;
      push.call(history, { ...history.state, [GUARD]: true }, "", window.location.href);
      armed = true;
    };
    // A confirmed link/save consumes the extra entry instead of leaving a duplicate in Back history.
    const guardedPush: History["pushState"] = (state, title, url) => {
      if (armed && !dirty.current) replace.call(history, state, title, url);
      else push.call(history, state, title, url);
      armed = false;
    };
    const guardedReplace: History["replaceState"] = (state, title, url) => {
      const sameUrl = url == null || new URL(String(url), location.href).href === location.href;
      replace.call(history, armed && sameUrl ? { ...state, [GUARD]: true } : state, title, url);
      if (!sameUrl) armed = false;
    };
    history.pushState = guardedPush;
    history.replaceState = guardedReplace;
    function beforeUnload(event: BeforeUnloadEvent) {
      if (dirty.current) { event.preventDefault(); event.returnValue = ""; }
    }
    function beforeLink(event: MouseEvent) {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = event.target instanceof Element ? event.target.closest("a[href]") : null;
      if (!(anchor instanceof HTMLAnchorElement) || anchor.target === "_blank" || anchor.hasAttribute("download")) return;
      if (anchor.href === window.location.href || (anchor.pathname === window.location.pathname && anchor.search === window.location.search && anchor.hash)) return;
      if (!confirmLeave()) { event.preventDefault(); event.stopPropagation(); }
    }
    function beforeTraverse(event: PopStateEvent) {
      if (!armed) return;
      event.stopImmediatePropagation();
      if (restoring) { restoring = false; return; }
      if (confirmLeave()) { armed = false; history.back(); }
      else { restoring = true; history.forward(); }
    }
    window.addEventListener("beforeunload", beforeUnload);
    window.addEventListener("popstate", beforeTraverse, true);
    document.addEventListener("click", beforeLink, true);
    return () => {
      arm.current = () => {};
      window.removeEventListener("beforeunload", beforeUnload);
      window.removeEventListener("popstate", beforeTraverse, true);
      document.removeEventListener("click", beforeLink, true);
      if (history.pushState === guardedPush) history.pushState = push;
      if (history.replaceState === guardedReplace) history.replaceState = replace;
    };
  }, [confirmLeave]);
  return { markDirty, markSaved, confirmLeave };
}
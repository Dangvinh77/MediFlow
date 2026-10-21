"use client";
import { useCallback, useEffect, useRef } from "react";

/** Drafts stay in memory. Warn before link navigation, explicit Back, reload or close. */
export function useUnsavedChanges() {
  const dirty = useRef(false);
  const markDirty = useCallback(() => { dirty.current = true; }, []);
  const markSaved = useCallback(() => { dirty.current = false; }, []);
  const confirmLeave = useCallback(() => {
    if (!dirty.current) return true;
    if (!window.confirm("Bạn có dữ liệu chưa lưu. Rời trang và bỏ các thay đổi?")) return false;
    dirty.current = false;
    return true;
  }, []);
  useEffect(() => {
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
    window.addEventListener("beforeunload", beforeUnload);
    document.addEventListener("click", beforeLink, true);
    return () => { window.removeEventListener("beforeunload", beforeUnload); document.removeEventListener("click", beforeLink, true); };
  }, [confirmLeave]);
  return { markDirty, markSaved, confirmLeave };
}

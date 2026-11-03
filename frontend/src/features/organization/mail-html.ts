/** Defense in depth for the demo-only MIME preview. The iframe also has an opaque origin. */
export function safeMailHtml(html: string): string {
  const doc = new DOMParser().parseFromString(html, "text/html");
  doc.querySelectorAll("script,iframe,frame,object,embed,form,input,button,textarea,select,base,meta,link,svg,math,audio,video,source").forEach(node => node.remove());
  for (const node of doc.querySelectorAll("*")) {
    for (const attr of [...node.attributes]) {
      if (/^on/i.test(attr.name) || ["srcdoc", "srcset", "action", "formaction", "ping", "background", "xlink:href"].includes(attr.name.toLowerCase())) node.removeAttribute(attr.name);
    }
    if (node.hasAttribute("src") && !(node.tagName === "IMG" && /^data:image\/(png|jpeg|gif);base64,/i.test(node.getAttribute("src") ?? ""))) node.removeAttribute("src");
    if (node.hasAttribute("href")) {
      const href = node.getAttribute("href")!;
      node.removeAttribute("href");
      try {
        const url = new URL(href, window.location.origin);
        if (node.tagName === "A" && url.origin === window.location.origin && ["/login", "/account-access", "/reset-password", "/forgot-password"].includes(url.pathname)) {
          node.setAttribute("href", url.href); node.setAttribute("target", "_top"); node.setAttribute("rel", "noreferrer noopener");
        }
      } catch { /* Invalid links are rendered as text. */ }
    }
  }
  const policy = doc.createElement("meta");
  policy.httpEquiv = "Content-Security-Policy";
  policy.content = "default-src 'none'; img-src data:; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'";
  doc.head.prepend(policy);
  return `<!doctype html>${doc.documentElement.outerHTML}`;
}

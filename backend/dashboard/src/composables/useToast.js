/** Global toast helper for dashboard views. */
export function toast() {
  return window.__jehoToast || {
    success: () => {},
    warning: () => {},
    danger: () => {},
    info: () => {},
    push: () => {},
  }
}

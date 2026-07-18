/** 应用共用的四级弹层 z-index：Drawer、Modal、弹出控件和全局反馈依次升高。 */
export const OVERLAY_Z_INDEX = {
  drawer: 1100,
  modal: 1200,
  popup: 1300,
  feedback: 1400
} as const;

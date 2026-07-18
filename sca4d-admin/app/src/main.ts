import { createPinia } from "pinia";
import { createApp } from "vue";
import Antd from "ant-design-vue";
import "ant-design-vue/dist/reset.css";
import App from "./App.vue";
import { bindApiState } from "./api/client";
import { i18n } from "./i18n";
import { router } from "./router";
import { useSessionStore } from "./stores/session";
import { useLocaleStore } from "./stores/locale";
import { useThemeStore } from "./stores/theme";
import "./styles/theme.css";
import "./styles/app.css";

const app = createApp(App);
const pinia = createPinia();
app.use(pinia);
app.use(router);
app.use(Antd);
app.use(i18n);

const auth = useSessionStore(pinia);
const locale = useLocaleStore(pinia);
const theme = useThemeStore(pinia);
locale.restore();
auth.restore();
theme.restore();
bindApiState(auth);

app.mount("#app");

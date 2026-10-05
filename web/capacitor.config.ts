import type { CapacitorConfig } from '@capacitor/cli';

const devUrl = process.env.DEV_URL;

const config: CapacitorConfig = {
  appId: 'com.widgetly.app',
  appName: 'Widgetly',
  webDir: 'dist',
  server: devUrl
    ? { url: devUrl, cleartext: true }
    : { androidScheme: 'https' },
  android: {
    backgroundColor: '#14161a',
    path: '../android',
  },
};

export default config;

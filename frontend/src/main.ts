import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { registerLocaleData } from '@angular/common';
import localeEsCL from '@angular/common/locales/es-CL';
import { AppModule } from './app/app.module';

registerLocaleData(localeEsCL, 'es-CL');

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.error(err));

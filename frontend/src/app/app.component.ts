import { Component } from '@angular/core';
import { MsalService } from '@azure/msal-angular';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent {
  title = 'E-Commerce Tech';
  isLoggedIn = false;

  constructor(private msalService: MsalService) {
    this.checkLogin();
  }

  checkLogin(): void {
    this.isLoggedIn = this.msalService.instance.getAllAccounts().length > 0;
  }

  login(): void {
    this.msalService.loginPopup().subscribe(() => {
      this.checkLogin();
    });
  }

  logout(): void {
    this.msalService.logoutPopup().subscribe(() => {
      this.checkLogin();
    });
  }
}

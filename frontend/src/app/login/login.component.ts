import { Component } from '@angular/core';
import { MsalService } from '@azure/msal-angular';
import { environment } from '../../environments/environment';

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent {

  errorMessage = '';

  constructor(private msalService: MsalService) {}

  login(): void {
    this.errorMessage = '';

    this.msalService.loginRedirect({
      scopes: environment.scopes
    });
  }
}

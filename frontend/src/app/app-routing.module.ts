import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';
import { AdminRoleGuard } from './auth/admin-role.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { AdminComponent } from './admin/admin.component';
import { MyOrdersComponent } from './my-orders/my-orders.component';
import { RabbitmqAdminComponent } from './rabbitmq-admin/rabbitmq-admin.component';
import { KafkaAdminComponent } from './kafka-admin/kafka-admin.component';
import { ReportsComponent } from './reports/reports.component';
import { AuditComponent } from './audit/audit.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  // Requiere estar autenticado (cualquier usuario de Azure AD)
  {
    path: 'my-orders',
    component: MyOrdersComponent,
    canActivate: [MsalGuard]
  },
  {
    path: 'admin',
    component: AdminComponent,
    // MsalGuard: requiere estar autenticado (login)
    // AdminRoleGuard: ademas requiere rol 'admin' claim en el ID token
    canActivate: [MsalGuard, AdminRoleGuard]
  },
  {
    path: 'admin/rabbitmq',
    component: RabbitmqAdminComponent,
    canActivate: [MsalGuard, AdminRoleGuard]
  },
  {
    path: 'admin/kafka',
    component: KafkaAdminComponent,
    canActivate: [MsalGuard, AdminRoleGuard]
  },
  {
    path: 'admin/reports',
    component: ReportsComponent,
    canActivate: [MsalGuard, AdminRoleGuard]
  },
  {
    path: 'admin/audit',
    component: AuditComponent,
    canActivate: [MsalGuard, AdminRoleGuard]
  },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
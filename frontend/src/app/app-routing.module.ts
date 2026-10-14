import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { AuthGuard } from './auth/auth.guard';
import { AdminRoleGuard } from './auth/admin-role.guard';
import { HomeComponent } from './home/home.component';
import { LoginComponent } from './login/login.component';
import { RegisterComponent } from './register/register.component';
import { AdminComponent } from './admin/admin.component';
import { MyOrdersComponent } from './my-orders/my-orders.component';
import { RabbitmqAdminComponent } from './rabbitmq-admin/rabbitmq-admin.component';

const routes: Routes = [
  { path: '', component: HomeComponent },
  { path: 'login', component: LoginComponent },
  { path: 'register', component: RegisterComponent },
  // Requiere estar autenticado (cuenta local o Microsoft)
  {
    path: 'my-orders',
    component: MyOrdersComponent,
    canActivate: [AuthGuard]
  },
  {
    path: 'admin',
    component: AdminComponent,
    // AuthGuard: requiere estar autenticado (login)
    // AdminRoleGuard: ademas requiere rol 'admin' en el token (local o Microsoft)
    canActivate: [AuthGuard, AdminRoleGuard]
  },
  {
    path: 'admin/rabbitmq',
    component: RabbitmqAdminComponent,
    canActivate: [AuthGuard, AdminRoleGuard]
  },
  { path: '**', redirectTo: '' }
];

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
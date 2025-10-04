import { Component, EventEmitter, Input, Output } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, NgForm } from '@angular/forms';

@Component({
    selector: 'urp-login',
    imports: [CommonModule, FormsModule],
    templateUrl: './login.component.html',
    styleUrls: ['./login.component.css']
})
export class LoginComponent {
  @Input() isLoading = false;
  @Input() error: string | null = null;
  @Output() login = new EventEmitter<{ username: string; password: string }>();

  submit(form: NgForm): void {
    if (form.invalid) {
      return;
    }
    const { username, password } = form.value;
    this.login.emit({ username, password });
    form.resetForm();
  }
}

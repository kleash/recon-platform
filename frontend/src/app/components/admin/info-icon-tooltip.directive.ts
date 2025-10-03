import { AfterViewInit, Directive, ElementRef, Renderer2 } from '@angular/core';

let tooltipIdSeed = 0;

@Directive({
  standalone: true,
  selector: 'span.info-icon[title]'
})
export class InfoIconTooltipDirective implements AfterViewInit {
  constructor(private readonly elementRef: ElementRef<HTMLElement>, private readonly renderer: Renderer2) {}

  ngAfterViewInit(): void {
    const host = this.elementRef.nativeElement;
    const tooltip = host.getAttribute('title')?.trim();

    if (!tooltip) {
      host.removeAttribute('title');
      return;
    }

    const tooltipId = `info-icon-tooltip-${++tooltipIdSeed}`;

    const tooltipContainer = this.renderer.createElement('span');
    this.renderer.addClass(tooltipContainer, 'info-tooltip');
    this.renderer.setAttribute(tooltipContainer, 'role', 'tooltip');
    this.renderer.setAttribute(tooltipContainer, 'id', tooltipId);
    this.renderer.appendChild(tooltipContainer, this.renderer.createText(tooltip));

    this.renderer.appendChild(host, tooltipContainer);

    this.renderer.setAttribute(host, 'aria-describedby', tooltipId);
    this.renderer.setAttribute(host, 'aria-label', tooltip);

    if (!host.hasAttribute('tabindex')) {
      this.renderer.setAttribute(host, 'tabindex', '0');
    }

    this.renderer.removeAttribute(host, 'title');
  }
}

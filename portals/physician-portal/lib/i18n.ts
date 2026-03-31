import en from '../messages/en-US.json';
import es from '../messages/es-CO.json';

export function loadMessages(locale?: string): Record<string, string> {
  const loc = locale ?? 'es-CO';
  return loc.startsWith('en') ? (en as Record<string, string>) : (es as Record<string, string>);
}

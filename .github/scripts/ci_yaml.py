"""Safe workflow YAML loading: string `on`, boolean true/false, unique keys."""

import re

import yaml


class WorkflowLoader(yaml.SafeLoader):
    pass


WorkflowLoader.yaml_implicit_resolvers = {
    key: [(tag, pattern) for tag, pattern in entries
          if tag != 'tag:yaml.org,2002:bool']
    for key, entries in yaml.SafeLoader.yaml_implicit_resolvers.items()
}
WorkflowLoader.add_implicit_resolver(
    'tag:yaml.org,2002:bool', re.compile(r'^(?:true|false)$', re.I), list('tTfF'),
)


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if not isinstance(key, str):
            raise ValueError('YAML mapping keys must be strings')
        if key in result:
            raise ValueError(f'Duplicate YAML key: {key}')
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


WorkflowLoader.add_constructor('tag:yaml.org,2002:map', unique_mapping)


def load_yaml(path):
    with path.open() as stream:
        value = yaml.load(stream, Loader=WorkflowLoader)
    if not isinstance(value, dict):
        raise ValueError('Expected a YAML mapping')
    return value
